use wasm_bindgen::prelude::*;

const EARTH_RADIUS_KM: f64 = 6371.0088;

#[wasm_bindgen]
pub fn format_lat_lon(lat: f64, lon: f64, decimals: u32) -> String {
    let d = decimals.min(8) as usize;
    let ns = if lat >= 0.0 { "N" } else { "S" };
    let ew = if lon >= 0.0 { "E" } else { "W" };
    format!("{:.*}°{}, {:.*}°{}", d, lat.abs(), ns, d, lon.abs(), ew)
}

#[wasm_bindgen]
pub fn parse_latitude(text: &str) -> Option<f64> {
    parse_coordinate(text, true)
}

#[wasm_bindgen]
pub fn parse_longitude(text: &str) -> Option<f64> {
    parse_coordinate(text, false)
}

fn parse_coordinate(raw: &str, is_lat: bool) -> Option<f64> {
    let text = raw.trim().to_uppercase().replace('，', ",");
    if text.is_empty() {
        return None;
    }

    let mut body = text;
    let mut direction: Option<char> = None;
    if let Some(first) = body.chars().next() {
        if matches!(first, 'N' | 'S' | 'E' | 'W') {
            direction = Some(first);
            body = body[first.len_utf8()..].trim().to_string();
        }
    }
    if direction.is_none() {
        if let Some(last) = body.chars().last() {
            if matches!(last, 'N' | 'S' | 'E' | 'W') {
                direction = Some(last);
                body = body[..body.len() - last.len_utf8()].trim().to_string();
            }
        }
    }
    if body.chars().any(|c| matches!(c, 'N' | 'S' | 'E' | 'W')) {
        return None; // e.g. `35N 10`, `35 SOUTH`, or a second hemisphere
    }
    if let Some(d) = direction {
        let valid = if is_lat { matches!(d, 'N' | 'S') } else { matches!(d, 'E' | 'W') };
        if !valid {
            return None;
        }
    }

    let has_minsec = body.chars().any(|c| matches!(c, '\'' | '"' | '′' | '″' | ':'));
    let has_degree = body.contains('°');
    let mut normalized = body.clone();
    if !has_minsec && normalized.matches(',').count() == 1 {
        // `48,8587`, `48,8587 N`, `48,8587°N`
        normalized = normalized.replace(',', ".");
        normalized = normalized.replace('°', " ");
    }
    for c in ['°', '\'', '"', '′', '″', ':', ','] {
        normalized = normalized.replace(c, " ");
    }

    let tokens: Vec<&str> = normalized.split_whitespace().collect();
    if tokens.is_empty() || tokens.len() > 3 {
        return None;
    }
    let values: Vec<f64> = tokens
        .iter()
        .map(|token| token.parse::<f64>().ok())
        .collect::<Option<Vec<f64>>>()?;
    if values.iter().any(|v| !v.is_finite()) {
        return None;
    }

    let minutes_first = !has_degree
        && tokens.len() == 2
        && body.chars().any(|c| matches!(c, '\'' | '′'));
    let (degrees, minutes, seconds) = if values.len() == 1 {
        (values[0], 0.0, 0.0)
    } else if minutes_first {
        if values[0] < 0.0 || values[0] >= 60.0 || values[1] < 0.0 || values[1] >= 60.0 {
            return None;
        }
        (0.0, values[0], values[1])
    } else {
        if values[0].fract() != 0.0 {
            return None; // fractional degrees must be a single token
        }
        let minutes = values.get(1).copied().unwrap_or(0.0);
        let seconds = values.get(2).copied().unwrap_or(0.0);
        if minutes < 0.0 || minutes >= 60.0 || seconds < 0.0 || seconds >= 60.0 {
            return None;
        }
        (values[0], minutes, seconds)
    };

    let magnitude = degrees.abs() + minutes / 60.0 + seconds / 3600.0;
    let has_direction = direction.is_some();
    let token_negative = tokens[0].starts_with('-');
    if has_direction && token_negative {
        return None;
    }
    let negative = matches!(direction, Some('S') | Some('W')) || (!has_direction && token_negative);
    let value = if negative { -magnitude } else { magnitude };
    let valid = if is_lat {
        (-90.0..=90.0).contains(&value)
    } else {
        (-180.0..=180.0).contains(&value)
    };
    if valid { Some(value) } else { None }
}

#[wasm_bindgen]
pub fn haversine_km(lat1: f64, lon1: f64, lat2: f64, lon2: f64) -> f64 {
    let p1 = lat1.to_radians();
    let p2 = lat2.to_radians();
    let dp = (lat2 - lat1).to_radians();
    let dl = (lon2 - lon1).to_radians();
    let a = (dp / 2.0).sin().powi(2) + p1.cos() * p2.cos() * (dl / 2.0).sin().powi(2);
    2.0 * EARTH_RADIUS_KM * a.sqrt().asin()
}

/// Returns a flat [lon, lat, lon, lat, ...] great-circle arc.
#[wasm_bindgen]
pub fn great_circle_points(lat1: f64, lon1: f64, lat2: f64, lon2: f64, segments: u32) -> Vec<f64> {
    let n = segments.clamp(2, 512) as usize;
    let mut out = Vec::with_capacity(n * 2);
    let to_vec = |lat: f64, lon: f64| {
        let phi = lat.to_radians();
        let lambda = lon.to_radians();
        [phi.cos() * lambda.cos(), phi.cos() * lambda.sin(), phi.sin()]
    };
    let a = to_vec(lat1, lon1);
    let b = to_vec(lat2, lon2);
    let dot = (a[0] * b[0] + a[1] * b[1] + a[2] * b[2]).clamp(-1.0, 1.0);
    let omega = dot.acos();
    for i in 0..n {
        let t = i as f64 / (n - 1) as f64;
        let (x, y, z) = if omega.abs() < 1e-6 {
            (a[0], a[1], a[2])
        } else {
            let s1 = ((1.0 - t) * omega).sin() / omega.sin();
            let s2 = (t * omega).sin() / omega.sin();
            (a[0] * s1 + b[0] * s2, a[1] * s1 + b[1] * s2, a[2] * s1 + b[2] * s2)
        };
        let lat = z.atan2((x * x + y * y).sqrt()).to_degrees();
        let mut lon = y.atan2(x).to_degrees();
        if lon < -180.0 { lon += 360.0; }
        if lon > 180.0 { lon -= 360.0; }
        out.push(lon);
        out.push(lat.clamp(-90.0, 90.0));
    }
    // Always land exactly on the requested destination (including zero-length and
    // antipodal cases where the spherical interpolation is ill-conditioned).
    if out.len() >= 2 {
        let len = out.len();
        out[len - 2] = lon2;
        out[len - 1] = lat2;
    }
    out
}

/// Iterative Ramer-Douglas-Peucker on a flat [lon, lat, ...] array.
#[wasm_bindgen]
pub fn simplify_polyline(points: &[f64], epsilon_meters: f64) -> Vec<f64> {
    let n = points.len() / 2;
    if n <= 3 {
        return points.to_vec();
    }
    let mid = (n / 2) * 2 + 1;
    let lat = points[mid].to_radians();
    let mx = 111_320.0 * lat.cos().abs().max(0.1);
    let my = 111_132.0;
    let mut keep = vec![false; n];
    keep[0] = true;
    keep[n - 1] = true;
    let mut stack = vec![(0usize, n - 1usize)];
    while let Some((a, b)) = stack.pop() {
        if b <= a + 1 { continue; }
        let ax = points[a * 2] * mx;
        let ay = points[a * 2 + 1] * my;
        let bx = points[b * 2] * mx;
        let by = points[b * 2 + 1] * my;
        let dx = bx - ax;
        let dy = by - ay;
        let len2 = dx * dx + dy * dy;
        let mut best = 0.0;
        let mut best_i = a;
        for i in (a + 1)..b {
            let px = points[i * 2] * mx;
            let py = points[i * 2 + 1] * my;
            let t = if len2 < 1e-9 { 0.0 } else { (((px - ax) * dx + (py - ay) * dy) / len2).clamp(0.0, 1.0) };
            let ex = px - (ax + t * dx);
            let ey = py - (ay + t * dy);
            let d = (ex * ex + ey * ey).sqrt();
            if d > best { best = d; best_i = i; }
        }
        if best > epsilon_meters {
            keep[best_i] = true;
            stack.push((a, best_i));
            stack.push((best_i, b));
        }
    }
    let mut out = Vec::new();
    for i in 0..n {
        if keep[i] { out.push(points[i * 2]); out.push(points[i * 2 + 1]); }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn formats_with_hemispheres() {
        assert_eq!(format_lat_lon(35.6585, 139.7022, 5), "35.65850°N, 139.70220°E");
        assert_eq!(format_lat_lon(-33.8688, -151.2093, 3), "33.869°S, 151.209°W");
    }

    #[test]
    fn parses_decimal_and_dms() {
        assert_eq!(parse_latitude("35.6585"), Some(35.6585));
        assert_eq!(parse_longitude("139.7022"), Some(139.7022));
        assert!((parse_latitude("33°52'7.68\"S").unwrap() + 33.8688).abs() < 1e-6);
        assert!((parse_latitude("-33°52'7.68\"").unwrap() + 33.8688).abs() < 1e-6);
        assert!((parse_latitude("-0°30'0\"").unwrap() + 0.5).abs() < 1e-9);
        assert_eq!(parse_latitude("48,8587 N"), Some(48.8587));
        assert_eq!(parse_latitude("48,8587°N"), Some(48.8587));
    }

    #[test]
    fn rejects_bad_coordinates() {
        assert_eq!(parse_latitude("91"), None);
        assert_eq!(parse_longitude("180.5"), None);
        assert_eq!(parse_latitude("35.6W"), None);
        assert_eq!(parse_longitude("139.7S"), None);
        assert_eq!(parse_latitude("1.2.3"), None);
        assert_eq!(parse_latitude("35°60'0\"N"), None);
    }

    #[test]
    fn great_circle_endpoints() {
        let pts = great_circle_points(34.427, 135.244, 26.196, 127.646, 8);
        assert_eq!(pts.len(), 16);
        assert!((pts[0] - 135.244).abs() < 1e-6);
        assert!((pts[1] - 34.427).abs() < 1e-6);
        assert!((pts[14] - 127.646).abs() < 1e-6);
        assert!((pts[15] - 26.196).abs() < 1e-6);
    }

    #[test]
    fn rejects_ambiguous_or_non_numeric_dms() {
        assert_eq!(parse_latitude("35 abc"), None);
        assert_eq!(parse_latitude("35 SOUTH"), None);
        assert_eq!(parse_latitude("35N 10"), None);
        assert_eq!(parse_latitude("35N S"), None);
        assert_eq!(parse_longitude("10E W"), None);
        assert_eq!(parse_longitude("1E2"), None);
        assert_eq!(parse_latitude("35.5 30"), None);
        assert_eq!(parse_latitude("35.5N"), Some(35.5));
        assert_eq!(parse_latitude("s 33.8688"), Some(-33.8688));
    }

    #[test]
    fn minutes_first_dms_without_degrees() {
        let v = parse_latitude("52'7\"S").unwrap();
        assert!((v + 0.8686111111111111).abs() < 1e-9);
    }

    #[test]
    fn simplifier_preserves_endpoints() {
        let pts = vec![139.5, 35.3, 139.5005, 35.3, 139.501, 35.3, 139.52, 35.3, 139.52, 35.33];
        let out = simplify_polyline(&pts, 150.0);
        assert_eq!(out[0], 139.5);
        assert_eq!(out[out.len() - 2], 139.52);
        assert!(out.len() < pts.len());
    }
}
