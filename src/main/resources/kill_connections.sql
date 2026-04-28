-- Supabase 기존 연결 종료 스크립트
-- Supabase SQL Editor에서 실행하세요

-- 1. 현재 연결 상태 확인
SELECT
    pid,
    usename,
    application_name,
    client_addr,
    state,
    query_start,
    state_change,
    wait_event_type,
    wait_event,
    query
FROM pg_stat_activity
WHERE datname = current_database()
ORDER BY state_change DESC;

-- 2. idle 상태인 연결 개수 확인
SELECT
    state,
    COUNT(*) as connection_count
FROM pg_stat_activity
WHERE datname = current_database()
GROUP BY state;

-- 3. 오래된 idle 연결 종료 (10분 이상)
SELECT
    pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = current_database()
  AND state = 'idle'
  AND state_change < NOW() - INTERVAL '10 minutes'
  AND pid <> pg_backend_pid();

-- 4. 모든 idle 연결 종료 (긴급 상황)
-- 주의: 실행 중인 다른 애플리케이션도 영향받을 수 있습니다
-- SELECT
--     pg_terminate_backend(pid)
-- FROM pg_stat_activity
-- WHERE datname = current_database()
--   AND state = 'idle'
--   AND pid <> pg_backend_pid();

-- 5. 특정 애플리케이션 연결만 종료 (CoupangRanking)
SELECT
    pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = current_database()
  AND application_name = 'CoupangRanking'
  AND pid <> pg_backend_pid();

-- 6. 최종 연결 상태 확인
SELECT
    state,
    COUNT(*) as connection_count
FROM pg_stat_activity
WHERE datname = current_database()
GROUP BY state;
