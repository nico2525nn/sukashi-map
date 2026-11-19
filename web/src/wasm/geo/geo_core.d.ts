/* tslint:disable */
/* eslint-disable */

export function format_lat_lon(lat: number, lon: number, decimals: number): string;

/**
 * Returns a flat [lon, lat, lon, lat, ...] great-circle arc.
 */
export function great_circle_points(lat1: number, lon1: number, lat2: number, lon2: number, segments: number): Float64Array;

export function haversine_km(lat1: number, lon1: number, lat2: number, lon2: number): number;

export function parse_latitude(text: string): number | undefined;

export function parse_longitude(text: string): number | undefined;

/**
 * Iterative Ramer-Douglas-Peucker on a flat [lon, lat, ...] array.
 */
export function simplify_polyline(points: Float64Array | number[], epsilon_meters: number): Float64Array;

export type InitInput = RequestInfo | URL | Response | BufferSource | WebAssembly.Module;

export interface InitOutput {
    readonly memory: WebAssembly.Memory;
    readonly format_lat_lon: (a: number, b: number, c: number) => [number, number];
    readonly great_circle_points: (a: number, b: number, c: number, d: number, e: number) => [number, number];
    readonly haversine_km: (a: number, b: number, c: number, d: number) => number;
    readonly parse_latitude: (a: number, b: number) => [number, number];
    readonly parse_longitude: (a: number, b: number) => [number, number];
    readonly simplify_polyline: (a: number, b: number, c: number) => [number, number];
    readonly __wbindgen_externrefs: WebAssembly.Table;
    readonly __wbindgen_free: (a: number, b: number, c: number) => void;
    readonly __wbindgen_malloc: (a: number, b: number) => number;
    readonly __wbindgen_realloc: (a: number, b: number, c: number, d: number) => number;
    readonly __wbindgen_start: () => void;
}

export type SyncInitInput = BufferSource | WebAssembly.Module;

/**
 * Instantiates the given `module`, which can either be bytes or
 * a precompiled `WebAssembly.Module`.
 *
 * @param {{ module: SyncInitInput }} module - Passing `SyncInitInput` directly is deprecated.
 *
 * @returns {InitOutput}
 */
export function initSync(module: { module: SyncInitInput } | SyncInitInput): InitOutput;

/**
 * If `module_or_path` is {RequestInfo} or {URL}, makes a request and
 * for everything else, calls `WebAssembly.instantiate` directly.
 *
 * @param {{ module_or_path: InitInput | Promise<InitInput> }} module_or_path - Passing `InitInput` directly is deprecated.
 *
 * @returns {Promise<InitOutput>}
 */
export default function __wbg_init (module_or_path?: { module_or_path: InitInput | Promise<InitInput> } | InitInput | Promise<InitInput>): Promise<InitOutput>;
