package com.p2pmsgcore;

/**
 * {@code p2pcng::IdResult} — what the identity store says about loading, creating
 * or protecting a key file. Returned as an {@code int} by
 * {@link P2PeerHub#setIdentity}, {@link P2PeerHub#setAllowList},
 * {@link P2PeerHub#reloadAllowList} and {@link P2PeerHub#provisionAuth}.
 *
 * <p><b>These names are a COPY, and copies drift.</b> The flat C ABI exports
 * {@code p2peerhub_auth_arm_text} so an FFI caller can render an {@link ArmResult}
 * in the library's own words, but it exports no equivalent for this enum — so the
 * fourteen names below are transcribed from {@code P2PIdentityStore.h}, a C++
 * header no FFI consumer can see, and nothing checks that they still line up.
 * {@link #fromCode} therefore refuses an unknown value loudly rather than
 * inventing a name for it: a code this enum does not know means the library's
 * enum has grown, and the safe reading of an unknown identity-store result is
 * "not OK".
 */
public enum IdResult {

    /** Success. */
    OK(0),
    /** Null, empty or oversized argument. */
    ERR_ARGS(1),
    /** Open, read, write or rename failed. */
    ERR_IO(2),
    /** The file does not exist. */
    ERR_NOT_FOUND(3),
    /** Exclusive create, and the file is already there. */
    ERR_EXISTS(4),
    /** Bad magic, truncated, or an unparsable text line. */
    ERR_FORMAT(5),
    /** A container version this build does not know. */
    ERR_VERSION(6),
    /** Digest mismatch — the file was corrupted. */
    ERR_INTEGRITY(7),
    /** {@code CryptProtectData} failed. */
    ERR_PROTECT(8),
    /** {@code CryptUnprotectData} failed: wrong machine or user, wrong entropy, or tampering. */
    ERR_UNPROTECT(9),
    /** The key material is not a valid P-256 identity. */
    ERR_KEY(10),
    /** POSIX only: the identity file is group- or world-accessible and was refused. */
    ERR_PERMS(11),
    /** Unsupported, e.g. a DPAPI-protected file read on POSIX. */
    ERR_UNSUPPORTED(12),
    /** The key is on the revocation list. */
    ERR_REVOKED(13);

    private final int code;

    IdResult(int code) { this.code = code; }

    /** The raw {@code p2pcng::IdResult} value. */
    public int code() { return code; }

    /** True only for {@link #OK}. */
    public boolean ok() { return this == OK; }

    /** Maps a raw {@code IdResult} int to a constant, refusing one it does not know. */
    public static IdResult fromCode(int code) {
        for (IdResult r : values()) if (r.code == code) return r;
        throw new IllegalArgumentException("unknown IdResult " + code
                + " -- p2pcng::IdResult has grown and this enum has not; treat it as an error");
    }
}
