-- 선택한 상품 등록 SQL
-- PostgreSQL 쿼리

-- 1. 기존 선택 상품 모두 삭제 (선택사항 - 기존 선택을 유지하려면 주석 처리)
DELETE FROM favorite_products;

-- 2. ItemId 기반으로 선택 상품 등록
-- display_order는 순서대로 1, 2, 3... 자동 부여
INSERT INTO favorite_products (product_id, display_order, created_at)
SELECT
    p.id,
    ROW_NUMBER() OVER (ORDER BY p.id) as display_order,
    NOW() as created_at
FROM products p
WHERE p.item_id IN (
    '24922307212',
    '24915072273',
    '23850926037',
    '19745910582',
    '23750602915',
    '20090773386',
    '26579740821',
    '24339114931',
    '25436367397',
    '24915072273',
    '24694517684',
    '25481770030',
    '23846843503',
    '23241823702',
    '23753283738',
    '24559961680',
    '22829929040',
    '24909899397',
    '24770910674',
    '22599424872',
    '24202291934',
    '22829181897',
    '24991184475',
    '19658681729',
    '5454803775',
    '84510271',
    '98053886',
    '68819382',
    '15486343623',
    '19432769379',
    '17975563',
    '20724478541',
    '21273028583',
    '21426917374',
    '11168924196',
    '20406833637',
    '2247709233',
    '21965642505',
    '26266671217',
    '13827276963',
    '2181792255',
    '11949145423',
    '24244212101',
    '12658911363',
    '24781648913',
    '9701511776',
    '24132153783',
    '9887555546',
    '11420191241',
    '19432688168',
    '23501142364',
    '20773781027',
    '19165513632',
    '26147645855',
    '2920186507',
    '24724430765',
    '20352593341',
    '18294745136'
);

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
