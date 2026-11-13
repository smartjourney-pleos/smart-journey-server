-- KPI 일 집계 스냅샷 (Phase 3 · 3-5)
-- 원본 기록(journey_event, waiting, arrival_check …)은 보유 기간(30일) 뒤 파기되므로
-- 파기 전에 하루 단위 숫자만 남긴다. 개인을 알아볼 수 있는 값은 들어가지 않는다.
create table kpi_daily (
    business_date       date          primary key,   -- Asia/Seoul 영업일
    payload             varchar(4000) not null,      -- KpiDtos.DailyKpi JSON
    created_at          timestamp with time zone not null
);
