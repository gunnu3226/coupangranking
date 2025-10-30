-- 선택한 상품 등록 SQL
-- PostgreSQL 쿼리

-- 1. 기존 선택 상품 모두 삭제 (선택사항 - 기존 선택을 유지하려면 주석 처리)
DELETE FROM favorite_products;

-- 2. ItemId 기반으로 선택 상품 등록
-- display_order는 제공된 ItemId 리스트의 순서대로 부여
-- 중복된 ItemId의 경우 첫 번째 순서를 사용
INSERT INTO favorite_products (product_id, display_order, created_at)
SELECT
    p.id,
    min_order.display_order,
    NOW() as created_at
FROM (
    SELECT item_id, MIN(display_order) as display_order
    FROM (
        VALUES
            ('24922307212', 1),
            ('24915072273', 2),
            ('23850926037', 3),
            ('19745910582', 4),
            ('23750602915', 5),
            ('20090773386', 6),
            ('26579740821', 7),
            ('24339114931', 8),
            ('25436367397', 9),
            ('24915072273', 10),
            ('24694517684', 11),
            ('25481770030', 12),
            ('23846843503', 13),
            ('23241823702', 14),
            ('23753283738', 15),
            ('24559961680', 16),
            ('22829929040', 17),
            ('24909899397', 18),
            ('24770910674', 19),
            ('22599424872', 20),
            ('24202291934', 21),
            ('22829181897', 22),
            ('24991184475', 23),
            ('19658681729', 24),
            ('5454803775', 25),
            ('84510271', 26),
            ('98053886', 27),
            ('68819382', 28),
            ('15486343623', 29),
            ('19432769379', 30),
            ('17975563', 31),
            ('20724478541', 32),
            ('21273028583', 33),
            ('21426917374', 34),
            ('11168924196', 35),
            ('20406833637', 36),
            ('2247709233', 37),
            ('21965642505', 38),
            ('26266671217', 39),
            ('13827276963', 40),
            ('2181792255', 41),
            ('11949145423', 42),
            ('24244212101', 43),
            ('12658911363', 44),
            ('24781648913', 45),
            ('9701511776', 46),
            ('24132153783', 47),
            ('9887555546', 48),
            ('11420191241', 49),
            ('19432688168', 50),
            ('23501142364', 51),
            ('20773781027', 52),
            ('19165513632', 53),
            ('26147645855', 54),
            ('2920186507', 55),
            ('24724430765', 56),
            ('20352593341', 57),
            ('18294745136', 58)
    ) AS t(item_id, display_order)
    GROUP BY item_id
) AS min_order
JOIN products p ON p.item_id = min_order.item_id
ORDER BY min_order.display_order;

-- 3. 등록된 상품 확인
SELECT
    fp.id,
    fp.display_order,
    p.item_id,
    p.product_name,
    p.company
FROM favorite_products fp
JOIN products p ON fp.product_id = p.id
ORDER BY fp.display_order;

-- 4. 등록 통계
SELECT
    COUNT(*) as total_favorites,
    COUNT(DISTINCT p.company) as companies_count
FROM favorite_products fp
JOIN products p ON fp.product_id = p.id;
