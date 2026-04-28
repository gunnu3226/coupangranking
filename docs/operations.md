# 운영/빌드 가이드

## 기본 명령어

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew bootRun
```

Gradle 빌드 시 `FE`의 npm install/build가 자동으로 실행되고, 빌드 결과가 `src/main/resources/static`에 생성됩니다.

```bash
./gradlew bootRun
```

```bash
./gradlew test
```

```bash
./gradlew clean build
```

애플리케이션 기본 포트는 Spring Boot 기본값인 `8080`입니다.

## 프론트엔드 개발 서버

Spring Boot 서버를 `8080`에서 실행한 뒤 별도 터미널에서 실행합니다.

```bash
cd FE
npm install
npm run dev
```

Vite 개발 서버는 기본 `5173` 포트를 사용하며 `/api`, `/health` 요청을 `http://localhost:8080`으로 프록시합니다.

## Docker

`Dockerfile`은 빌드된 jar를 이미지에 복사해서 실행합니다.

```dockerfile
FROM openjdk:17-alpine
COPY ./build/libs/coupang-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
```

이미지 빌드 전 `./gradlew clean build`로 jar를 먼저 생성해야 합니다.

## 설정 파일

주 설정 파일:

```text
src/main/resources/application.yml
```

주요 설정:

- multipart 최대 파일 크기: 50MB
- datasource: PostgreSQL
- JPA ddl-auto: update
- Hibernate SQL 로그 관련 설정 일부 주석 처리

## 보안 주의사항

현재 `application.yml`에 DB URL, username, password가 직접 들어 있습니다. 다음 작업을 권장합니다.

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

위 환경 변수 기반 설정으로 전환합니다.

예시:

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
```

이미 커밋된 secret은 단순히 파일에서 제거하는 것만으로는 충분하지 않을 수 있습니다. 외부에 공유된 저장소라면 DB 비밀번호 교체와 git history 정리를 검토해야 합니다.

## DB 운영 주의사항

`ddl-auto: update`는 개발 중에는 편하지만 운영에서는 위험할 수 있습니다.

운영 안정성을 높이려면:

- Flyway 또는 Liquibase 도입
- 운영 profile에서 `ddl-auto: validate` 또는 `none` 사용
- schema migration 파일로 변경 이력 관리

## 테스트 현황

현재 테스트는 `CoupangApplicationTests.contextLoads()`만 있습니다. 실질적인 파서/저장 로직 검증은 부족합니다.

우선순위 높은 테스트:

- 샘플 HTML 기반 `HtmlParserService.extractProductList()` 테스트
- `ProductDataService.saveProductData()`의 랭킹/광고/중복 저장 동작 테스트
- `FavoriteProductService.saveFavorites()`의 replace 전략 테스트
- Controller API smoke test

## 운영 헬스체크

`GET /health`

```json
{
  "status": "UP"
}
```

ECS/로드밸런서 헬스체크에 사용할 수 있습니다.

## 현재 배포 관련 파일

- `Dockerfile`
- `gradlew`, `gradlew.bat`
- `gradle/wrapper/*`

`coupang_key.pem` 파일도 루트에 존재합니다. 개인 키/접속 키 성격의 파일이라면 저장소에서 제거하고 별도 secret 관리가 필요합니다.
