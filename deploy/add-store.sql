-- 운영 매장 한 곳 넣기 (postgres 프로필은 데모 시드를 안 넣는다 · 4-4)
-- PIN 은 pgcrypto bcrypt 로 해시해서 넣는다 → 서버의 BCryptPasswordEncoder 가 그대로 검증
--
--   sudo -u postgres psql -d smartjourney \
--     -v name='A 맛집' -v address='경기도 시흥시 정왕동 …' -v lat=37.3401 -v lng=126.7335 -v pin=4821 \
--     -f deploy/add-store.sql
--
-- 선택 변수 (안 넣으면 기본값): -v minutes_per_team=5 -v arrival_buffer_min=10 -v avg_stay_min=60
--                              -v open_time=11:00 -v close_time=21:00
-- 실제 상호·로고는 제휴 전에는 쓰지 않는다 (디자인 가이드 10장)

\set ON_ERROR_STOP on
\if :{?minutes_per_team} \else \set minutes_per_team 5 \endif
\if :{?arrival_buffer_min} \else \set arrival_buffer_min 10 \endif
\if :{?avg_stay_min} \else \set avg_stay_min 60 \endif
\if :{?open_time} \else \set open_time '11:00' \endif
\if :{?close_time} \else \set close_time '21:00' \endif

create extension if not exists pgcrypto;

insert into store (name, address, latitude, longitude, poi_id, poi_sub_id,
                   minutes_per_team, arrival_buffer_min, manual_wait_min, max_defers, avg_stay_min,
                   open_time, close_time, pos_pin_hash, created_at, updated_at)
select :'name', :'address', :lat, :lng, null, '0',
       :minutes_per_team, :arrival_buffer_min, null, 2, :avg_stay_min,
       cast(:'open_time' as time), cast(:'close_time' as time),
       crypt(:'pin', gen_salt('bf', 10)),
       now(), now()
where not exists (select 1 from store where name = :'name')
returning id, name;
