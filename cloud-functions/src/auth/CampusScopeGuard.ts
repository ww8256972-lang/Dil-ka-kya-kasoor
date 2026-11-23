export const WCI_CAMPUS_ID = "WCI_UNNAO_MAIN";
export const WCI_CAMPUS_LAT = 26.538162;
export const WCI_CAMPUS_LNG = 80.484187;
export const WCI_GEOFENCE_RADIUS_METERS = 50.0;

/**
 * CampusScopeGuard (PRD Rule 3 & S-44)
 * Hard-locks all cloud function invocations and database operations to WCI_UNNAO_MAIN.
 */
export function assertCampusScope(campusId?: string): void {
  if (!campusId || campusId !== WCI_CAMPUS_ID) {
    throw new Error(`SECURITY_VIOLATION: Multi-branch access prohibited. Must be '${WCI_CAMPUS_ID}'.`);
  }
}

export function isWithinCampusGeofence(lat: number, lng: number): boolean {
  const R = 6371000; // Earth radius in meters
  const dLat = (lat - WCI_CAMPUS_LAT) * (Math.PI / 180);
  const dLng = (lng - WCI_CAMPUS_LNG) * (Math.PI / 180);
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(WCI_CAMPUS_LAT * (Math.PI / 180)) *
      Math.cos(lat * (Math.PI / 180)) *
      Math.sin(dLng / 2) *
      Math.sin(dLng / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  const distance = R * c;
  return distance <= WCI_GEOFENCE_RADIUS_METERS;
}
