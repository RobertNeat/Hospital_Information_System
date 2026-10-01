package robert_neat.his_backend.common.api;

/**
 * Kody bledow zgodne z unia `ApiErrorCode` z kontraktu (models/api/common.api.ts).
 * Wartosc na drucie to dokladnie nazwa stalej.
 */
public enum ApiErrorCode {
    NOT_FOUND,
    VALIDATION_FAILED,
    CONFLICT,
    FORBIDDEN,
    UNAUTHENTICATED,
    INTERNAL
}
