-- 시연·로컬 개발용 매장. 차량 앱(smart-journey-android FakeBackend)과 같은 장소·좌표·POI를 쓴다.
-- 장소·POI 출처: Pleos-Playground/pleos-sample-navi-app NaviTestData (에뮬레이터 내비에서 경로 확인).
-- 웨이팅·영업 정보는 실제가 아닌 시연 데이터다.
-- POS PIN: 두 매장 모두 1234  (BCrypt 해시)
-- local / test 프로필에서만 로드된다(postgres 프로필은 로드 안 함).
-- Flyway repeatable 마이그레이션이라 내용이 바뀌면 다시 실행된다 → 반드시 중복 없이 들어가게 작성.

-- 이전 시드(A 맛집·B 카페, 시흥)로 만든 로컬 DB는 같은 행을 새 장소로 바꾼다 (id 1·2 유지)
update store set name = '42dot', address = '경기도 성남시 수정구 창업로40번길 20',
                 latitude = 37.41309722, longitude = 127.09383464,
                 poi_id = '36khp37hh8gqcm5nqhe5', poi_sub_id = '0', updated_at = current_timestamp
where name = 'A 맛집';

update store set name = '소프트웨어드림센터', address = '경기도 성남시 수정구 창업로40번길 20',
                 latitude = 37.41310555, longitude = 127.094275,
                 poi_id = '36khp2hdj2cfcm5nqhe3', poi_sub_id = '0', updated_at = current_timestamp
where name = 'B 카페';

insert into store (name, address, latitude, longitude, poi_id, poi_sub_id,
                   minutes_per_team, arrival_buffer_min, manual_wait_min, max_defers, avg_stay_min,
                   open_time, close_time, pos_pin_hash, created_at, updated_at)
select '42dot', '경기도 성남시 수정구 창업로40번길 20', 37.41309722, 127.09383464, '36khp37hh8gqcm5nqhe5', '0',
       5, 10, null, 2, 60,
       time '11:00:00', time '21:00:00',
       '$2a$10$QCPRTyiR6FbBxsj3pt9ujuhNRdcwK4vgCM5Y6.i6.IFAkl6LEFwOe',
       current_timestamp, current_timestamp
where not exists (select 1 from store where name = '42dot');

insert into store (name, address, latitude, longitude, poi_id, poi_sub_id,
                   minutes_per_team, arrival_buffer_min, manual_wait_min, max_defers, avg_stay_min,
                   open_time, close_time, pos_pin_hash, created_at, updated_at)
select '소프트웨어드림센터', '경기도 성남시 수정구 창업로40번길 20', 37.41310555, 127.094275, '36khp2hdj2cfcm5nqhe3', '0',
       3, 10, null, 2, 40,
       time '10:00:00', time '22:00:00',
       '$2a$10$QCPRTyiR6FbBxsj3pt9ujuhNRdcwK4vgCM5Y6.i6.IFAkl6LEFwOe',
       current_timestamp, current_timestamp
where not exists (select 1 from store where name = '소프트웨어드림센터');
