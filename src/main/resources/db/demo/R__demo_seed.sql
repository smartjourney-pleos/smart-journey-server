-- 시연·로컬 개발용 가상 매장 (실제 상호·로고 사용 금지 — 디자인 가이드 10장 7번)
-- POS PIN: 두 매장 모두 1234  (BCrypt 해시)
-- 좌표는 시연 동선(에뮬레이터 경로 시뮬레이션)에 맞게 바꿔서 쓴다.
-- local / test 프로필에서만 로드된다(postgres 프로필은 로드 안 함).
-- Flyway repeatable 마이그레이션이라 내용이 바뀌면 다시 실행된다 → 반드시 중복 없이 들어가게 작성.

insert into store (name, address, latitude, longitude, poi_id, poi_sub_id,
                   minutes_per_team, arrival_buffer_min, manual_wait_min, max_defers, avg_stay_min,
                   open_time, close_time, pos_pin_hash, created_at, updated_at)
select 'A 맛집', '경기도 시흥시 정왕동 (데모 위치 1)', 37.3401, 126.7335, null, '0',
       5, 10, null, 2, 60,
       time '11:00:00', time '21:00:00',
       '$2a$10$QCPRTyiR6FbBxsj3pt9ujuhNRdcwK4vgCM5Y6.i6.IFAkl6LEFwOe',
       current_timestamp, current_timestamp
where not exists (select 1 from store where name = 'A 맛집');

insert into store (name, address, latitude, longitude, poi_id, poi_sub_id,
                   minutes_per_team, arrival_buffer_min, manual_wait_min, max_defers, avg_stay_min,
                   open_time, close_time, pos_pin_hash, created_at, updated_at)
select 'B 카페', '경기도 시흥시 정왕동 (데모 위치 2)', 37.3446, 126.7387, null, '0',
       3, 10, null, 2, 40,
       time '10:00:00', time '22:00:00',
       '$2a$10$QCPRTyiR6FbBxsj3pt9ujuhNRdcwK4vgCM5Y6.i6.IFAkl6LEFwOe',
       current_timestamp, current_timestamp
where not exists (select 1 from store where name = 'B 카페');
