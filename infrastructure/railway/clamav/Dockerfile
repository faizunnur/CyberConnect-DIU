# ClamAV daemon for Railway (docs/operations/railway.md, service "clamav"). Deployed from this directory so Railway
# builds it like any repo service; the pinned upstream image is unchanged. Mount a volume at /var/lib/clamav.
FROM clamav/clamav:1.4.6
ENV CLAMAV_NO_MILTERD=true
