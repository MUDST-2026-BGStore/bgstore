ALTER TABLE branch
    ADD COLUMN address VARCHAR(300),
    ADD COLUMN opens_at TIME,
    ADD COLUMN closes_at TIME;

UPDATE branch
SET address = '160 ถ. พระรามที่ 2 แขวงแสมดำ เขตบางขุนเทียน กรุงเทพฯ 10150',
    opens_at = TIME '09:00',
    closes_at = TIME '19:00'
WHERE id = '3f0d7d5a-9a2b-4a71-8f0e-000000000001';

UPDATE branch
SET address = '999/9 ถ. พระรามที่ 1 แขวงปทุมวัน เขตปทุมวัน กรุงเทพฯ 10330',
    opens_at = TIME '10:00',
    closes_at = TIME '20:00'
WHERE id = '3f0d7d5a-9a2b-4a71-8f0e-000000000002';

UPDATE branch
SET address = '999/9 ถ. พระรามที่ 9 แขวงห้วยขวาง เขตห้วยขวาง กรุงเทพฯ 10310',
    opens_at = TIME '10:00',
    closes_at = TIME '21:00'
WHERE id = '3f0d7d5a-9a2b-4a71-8f0e-000000000003';
