# Route behavior shared by Android and Web

The Android and Web renderers are separate implementations, but the decisions
that change the route must match. Update this document and both test suites
whenever a rule changes.

## Photo location as a road waypoint

A photo location is inserted between departure and destination only when all of
the following are true:

- the photo coordinate exists,
- it is more than `0.01` degrees from both endpoints,
- `distance(dep, photo) + distance(photo, dest) <= 1.45 * distance(dep, dest)`.

The last rule keeps a distant photo from turning a direct drive into a huge
detour.

- Android: `RoutingAndPlaceService.shouldUsePhotoAsVia`
- Web: `fetchDrivingRoute` in `web/src/lib/data.ts`
- Tests: `SukashiCoreUnitTest.photoWaypointIsOnlyUsedWhenItDoesNotCreateALargeDetour`,
  `web/src/lib/__tests__/data.test.ts`

## Railway geometry

Geometry is stored per **company + line**, never by line name alone. The same
name is used by unrelated operators (for example JR East, JR Central and JR
West all have `東海道線`). Web station search must filter by both line and
company; Android route calculation already keys geometry by
`company::line`.

- A station must be within `0.05` degrees (about 5.5 km) of a point on the line.
- If one chain contains both stations within `0.02` degrees, follow that chain
  directly instead of searching the whole graph.
- Otherwise the station connects to the nearest point on each chain (never to
  every point in the radius) and the shortest connected path is used.
- Adjacent fragments may be linked when their endpoints are within `0.09`
  degrees (about 10 km).
- Consecutive points farther than `0.9` degrees are treated as corrupted data,
  not as track.
- Lines longer than `0.002` degrees are kept, so short railways such as Hakone
  Tozan are not discarded as noise.
- If no path exists, the selector returns nothing and the UI draws the
  explicitly labelled straight line; returning every fragment is forbidden.

- Generator: `web/scripts/build-rail-geometry.py`
- Selector: `web/src/lib/rail-geometry.ts`
- Tests: `web/src/lib/__tests__/rail-geometry.test.ts`

## Map dataset selection

The map loader and renderer both use the active transport mode:

- `AIRPLANE`: departure and arrival airports,
- `TRAIN`: departure and arrival stations,
- `OTHER`: departure and destination places,
- `SPOT`: the current GPS / photo coordinate,
- plus the photo coordinate as a fallback anchor.

Web exposes this as `mapAnchorPoints` in `web/src/types.ts`. Android mirrors it
with `TransportInfo.startCoord`, `endCoord` and `currentGpsCoord`.
