# Spring playground

### 이벤트 처리량 개선, Reactive programming / Kafka batch queue
- Pulsar로부터 수신한 이벤트를 1. 외부 Api, 2. MongoDB, 3. Kafka에 전달해야 한다.
- 초당 2000개의 이벤트 처리를 목표로 했다.
- WebClient, Reative Mongo는 Netty의 이벤트 루프 스레드로 관리되어 처리 완료 시 또는 예외 시 콜백 기반으로 이후 처리 로직이 수행된다.
- Netty 이벤트 루프 스레드는 기본 값으로 CPU 코어 수에 따라 스레드 풀이 생성되고, 직접 설정할 수 있다.
- Epoll 등 커널 수준의 이벤트 IO 전달(멀티 플렉싱)을 사용하여 이벤트가 발생했을 때를 핸들링하기에, 이벤트 발생을 대기하는 기존 멀티 스레딩 방식보다 자원 효율이 좋고, 더 적은 수의 스레드로 처리가 가능하다.
``` 
// 스레드 이름 예시
WebClient : reactor-http-nio-1
Reactive Mongo : nioEventLoopGroup-2-16
```
- Kafka producer의 이벤트 전달 역시 그 결과를 대기하지 않는다.
- 그렇지만 그 동작 방식은 멀티 플렉싱을 사용한 앞선 WebClient, Reactive Mongo와는 다르다.
- Kafka produce는 전달할 이벤트를 담을 큐(batch queue)를 메모리에 생성해두고 이를 관리하는 스레드를 따로 둔다.
- 그 스레드는 일정 시간 간격 또는 큐에 쌓인 이벤트 크기(batch size)를 기준으로 큐의 이벤트를 배치 발송한다.
- 카프카 서버로의 이벤트 전달에 필요한 네트워크 비용을 최소화한다.
```
// Batch queue 관리 스레드 이름 예시
Kafka producer : kafka-producer-network-thread | producer-1
```

### Sliding window rate limiter
- 사용자별 분당 요청 수를 제한한다.
- lua script로 경쟁 조건을 피하고 원자적 연산을 수행했다.
- ZSet의 Score를 요청 시간으로 하여 조건 시간내 개수 검색과 요소 제거 성능을 높인다.
- Zset은 Skip list와 Hash Table으로 구현되어 있다.
- Skip list는 Linked list를 계층화하고, 랜덤하게 뽑힌 노드들을 연결하는 Express Line으로 요소를 건너뛸 수 있는 포인트를 두어 검색 성능을 높인다.

``` lua
local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local maxReq = tonumber(ARGV[3])
local uuid = ARGV[4]

redis.call('ZREMRANGEBYSCORE', key, 0, now-window)
local cnt = redis.call('ZCOUNT', key, now-window+1, now)
if cnt >= maxReq then
  return 0
else
  redis.call('ZADD', key, now, uuid)
  redis.call('EXPIRE', key, window+10)
  return 1
end
```

### WebClient, 이벤트 전달
- 이벤트를 단순히 전달하는 상황에서 WebClient를 사용했다.
- 기존 블록킹 방식의 RestTemplate는 외부 API의 응답 시간에 영향을 받아 호출한 스레드가 응답을 기다리며 차단된다.
- 이로 인해 응답 시간과 상관없이 기존 처리 흐름을 유지하려면 멀티스레딩이 필요했다.
- 반면, WebClient는 논블로킹 방식으로 외부 API 요청을 처리한다.
- 호출한 스레드는 응답을 기다리지 않고 이후의 작업을 계속 처리할 수 있다.
- 응답 결과나 에러는 Mono/Flux 기반의 이벤트 스트림으로 처리된다.
- 매번 스레드를 사용하는 것이, 멀티플렉싱을 활용하기에 리소스 효율이 더 좋다.
- 요청과 응답 헤더에 TraceId를 삽입하고 이로 MDC를 대신하여 로깅하였다.

``` java
notificationClient.postAsAsync()
    .doOnError(WebClientResponseException.class, ex -> {
        log.error("Failed to deliver notification event : {}, status : {}, body : {}",
            ex.getHeaders().getFirst(TRACE_ID),
            ex.getStatusCode(),
            ex.getResponseBodyAsString()
        );
    }).subscribe();
        return user.getId();
    }
```

### MDC(Mapped Diagnostic Context)
- 한 요청의 처리 흐름이 단일 서버가 아닌, 여러 서버로 전파되는 경우 로깅이 까다롭다.
- 이를 맥락별로 한 ID를 공유하여 로깅될 수 있으면, 해당 ID를 검색하는 것으로 요청 전파 후 흐름을 따라가기 좋다.
- MDC는 ThreadLocal을 활용하여 요청을 처리하는 스레드 안에서 고유한 Trace Id를 관리하는 방법이다.
- Filter로 시작 시점에 UUID를 생성하여 MDC에 넣고, 그 값을 요청이 끝낼 떄까지 유지하여 로깅에 사용한다.
- 서버 간 통신 시, 헤더 (X-Trace-Id)에 동일한 값을 넣고 전달하여, 수신처에서도 Trace Id를 유지할 수 있도록 한다.
- ThreadLocal을 활용했기에, 요청 처리 시 MDC를 Clear한다.

``` java
@Override
public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
    try {
        var httpRequest = (HttpServletRequest) request;
        var traceId = httpRequest.getHeader(TRACE_ID_HEADER);
        if(traceId != null) {
            MDC.put(TRACE_ID, traceId);
            chain.doFilter(request, response);
        } else {
            var newTraceId = UUID.randomUUID().toString();
            MDC.put(TRACE_ID, newTraceId);
            chain.doFilter(request, response);
        }
    } finally {
        MDC.clear();
    }
}
```

### @InternalHandler
- 내부 통신을 위한 Api 요청 인증에 암호화된 키를 사용한다.
- 현재 시간을 AES로 암호화하고, 이를 헤더(X-Client-Key)에 담아 전달한다.
- 수신처에선 복호화가 가능한지 확인하고, 유효 시간 범위 내의 값인지 검증한다.
- 내부 통신을 위한 Api임을 표시할 어노테이션을 정의하고, Interceptor로 해당되는 핸들러의 키 검증 로직을 선처리한다.

``` java
@InternalHandler
@PostMapping("/api/user/credit")
public ApiResponse<Void> add(@RequestParam String username, @RequestParam Long addition) {
    creditService.addCredit(username, addition);
    return ApiResponse.success();
}
```

### Pessimistic lock
- 크레딧 업데이트 로직은 { 조회, 수정 }으로 트랜잭션이 이뤄진다.
- 만약 격리 수준을 Repeatable Read로 하는 상황에서 한 사용자로부터 동시에 여러 요청이 들어오는 경우, 두 번의 갱신 분실의 문제가 있을 수 있다.
- 두 번의 갱신 분실의 문제는, 두 개 이상의 트랜잭션이 동일한 데이터를 조회 후 수정하여 마지막 트랜잭션의 결과만이 반영되는 상황을 말한다.
- Mysql의 경우, Index를 기반으로 락을 걸리고, 아래 쿼리에서 사용자 정보(uid)는 Index로 등록되어 있다.
- 즉 동일한 사용자의 트랜잭션끼리 { 조회, 수정 }을 순차 처리하는 것으로, 두 번의 갱신 분실의 문제를 피한다.

``` java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT c FROM Credit c WHERE c.uid = :uid")
Optional<Credit> findByUidWithLock(@Param("uid") Long uid);
```

### Transaction service
- 크레딧을 업데이트하고 결제를 승인할 것인가, 결제를 승인하고 크레딧을 업데이트할 것인가.
- 전자는 결제 실패 시, 업데이트한 크레딧을 롤백 처리가 필요하고,
- 후자는 크레딧 업데이트 실패 시, 이미 빠져나간 돈을 반환하긴 쉽지 않다.
- 크레딧을 잘못 부여 후 회수에 실패하는 전자의 위험보다, 결제는 완료되었지만 크레딧은 그대로인 후자의 위험이 더 크다고 판단했다.

<img width="900" alt="image" src="https://github.com/user-attachments/assets/71d303cb-52ac-46c2-9b08-4736569378b6" />

### Service discovery
- 각 서비스는 애플리케이션 시작 시 Endpoint 정보를 Gateway에 알린다.
- Gateway는 서비스별 Endpoint 정보 리스트를 관리한다.
- 서비스 이름으로 서버간 통신이 가능하고, 이는 로드 밸런싱, 오토 스케일링을 가능하게 한다.
- 애플리케이션이 다운될 때 이를 Gateway 서버에 알려 리스트에서 제거한다.

``` java
public void register(String name, InetSocketAddress address) {
    if (SERVICE_ENDPOINTS.values().stream().anyMatch(set -> set.contains(address))) {
        throw new IllegalArgumentException("Address " + address + " already registered");
    }
    var serviceName = ServiceName.resolve(name);
    SERVICE_ENDPOINTS.computeIfAbsent(serviceName, k -> new ArrayList<>()).add(address);
    log.info("Registered service " + serviceName + " at " + address.getHostName() + ":" + address.getPort());
}
```

### AuthUser
- Gateway에서 Jwt 토큰을 확인한다.
- 토큰이 유효한지 확인하고, Payload에서 사용자 이름과 권한을 추출하여 Header { X-User-Id, X-User-Roles }에 담는다.
- 이는 각 서비스에서 불필요한 토큰 정보 추출과 유효성 검증을 하지 않도록 한다.
- 넘어간 유저 정보는 ArgumentResolver로 resolve되어 핸들러의 인자(AuthUser)로 전달된다.
- 인증이 필요한 핸들러에는 AuthUser가 인자로 표시되고, ArgumentResolve 과정에서 인증 정보 { X-User-Id, X-User-Roles }가 없으면 예외를 발생하여 엑세스를 막는다.

``` java
@Override
protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
    try {
        var token = getToken(request).orElseThrow(() -> new AuthException(ErrorType.TOKEN_NOT_FOUND));
        var accessToken = AccessToken.fromToken(TokenConfig.secretKey, token);
        checkBlocked(token, accessToken.username());

        var requestWrapper = new RequestWrapper(request);
        requestWrapper.addHeader("X-User-Id", accessToken.username());
        requestWrapper.addHeader("X-User-Roles", Arrays.toString(accessToken.roles()));

        filterChain.doFilter(requestWrapper, response);
    } catch (Exception e) {
        filterChain.doFilter(request, response);
    }
}
```
