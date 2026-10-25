FROM postgres:18-alpine

COPY scripts/seed/analytics-demo.sql /seed/analytics-demo.sql
COPY scripts/demo/run-seed.sh /seed/run-seed.sh

RUN chmod 0555 /seed/run-seed.sh
ENTRYPOINT ["/seed/run-seed.sh"]
