-- Baseline migration for the URL shortener service.
-- Only establishes the dedicated schema; short_urls and click_events are added
-- incrementally by later migrations together with the features that need them.

CREATE SCHEMA IF NOT EXISTS shortener;
