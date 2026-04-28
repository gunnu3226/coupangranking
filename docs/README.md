# Coupang 프로젝트 분석 문서

이 문서는 향후 기능 수정, 리팩터링, 운영 설정 변경을 빠르게 할 수 있도록 현재 코드 기준으로 정리한 유지보수용 문서 모음입니다.

## 문서 목록

- [프로젝트 개요](./project-overview.md): 애플리케이션 목적, 기술 스택, 주요 흐름
- [DB 구조](./database-schema.md): 엔티티, 테이블, 관계, 저장 규칙
- [백엔드 구조](./backend-architecture.md): 컨트롤러, 서비스, DTO, 리포지토리 책임
- [화면과 API 흐름](./frontend-and-api-flow.md): Thymeleaf 화면, REST API, 프론트 JS 동작
- [HTML 파싱 구현](./html-parsing.md): Jsoup 셀렉터, 파싱 필드, 변경 주의점
- [운영/빌드 가이드](./operations.md): 실행, 빌드, Docker, 설정, 보안 주의사항
- [변경 작업 가이드](./change-guide.md): 새 회사/카테고리/필드/API 추가 시 수정 위치

## 빠른 요약

이 프로젝트는 쿠팡 검색 결과 HTML을 사용자가 붙여 넣으면 상품 정보를 파싱하고, PostgreSQL에 일자별 랭킹 데이터를 저장하는 Spring Boot 애플리케이션입니다. 화면은 Thymeleaf 서버 렌더링 기반이며, 일부 버튼/정렬/저장 동작만 브라우저 JavaScript와 REST API로 처리합니다.

핵심 도메인은 `Product`, `ProductDailyData`, `FavoriteProduct` 세 개입니다. `Product`는 상품 마스터, `ProductDailyData`는 날짜별 스냅샷, `FavoriteProduct`는 사용자가 선택한 상품과 표시 순서를 저장합니다.

## 현재 확인된 주의사항

- `src/main/resources/application.yml`에 DB 접속 정보가 직접 들어 있습니다. 운영/공유 전 환경 변수 기반 설정으로 바꾸는 것이 필요합니다.
- Hibernate `ddl-auto: update`가 켜져 있어 엔티티 변경이 DB 스키마에 자동 반영됩니다. 운영 DB에서는 예기치 않은 스키마 변경 위험이 있습니다.
- QueryDSL 의존성은 있으나 현재 코드에서는 직접 사용하지 않습니다.
- 조회 로직 다수가 `findAll()` 후 Java Stream 필터링을 사용합니다. 데이터가 많아지면 리포지토리 쿼리로 옮기는 것이 좋습니다.
- 기존 `CLAUDE.md`에는 실제 현재 파서 셀렉터와 다른 설명이 일부 있습니다. 변경 판단은 이 `docs/` 문서를 우선 참고하세요.
