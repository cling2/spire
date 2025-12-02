package com.example.spiredemo.service;

import com.example.spiredemo.dto.SvidInfo;
import io.spiffe.exception.SocketEndpointAddressException;
import io.spiffe.exception.X509ContextException;
import io.spiffe.svid.x509svid.X509Svid;
import io.spiffe.workloadapi.DefaultWorkloadApiClient;
import io.spiffe.workloadapi.WorkloadApiClient;
import io.spiffe.workloadapi.X509Context;
import java.security.cert.X509Certificate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * SPIRE Workload API에서 현재 프로세스용 X509-SVID를 가져오는 서비스.
 */
@Service
public class SvidService {

    private static final Logger log = LoggerFactory.getLogger(SvidService.class);

    /**
     * Workload API에서 현재 프로세스의 SVID를 한 번 가져와서 리턴.
     */
    public SvidInfo fetchCurrentSvid() {
        String endpoint = System.getenv("SPIFFE_ENDPOINT_SOCKET");
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "unix:/tmp/spire-agent/public/api.sock";
            log.warn("SPIFFE_ENDPOINT_SOCKET 환경변수가 설정되지 않았습니다. 기본값({})을 사용합니다.", endpoint);
        }
        log.debug("SPIFFE_ENDPOINT_SOCKET = {}", endpoint);

        System.setProperty("spiffe.endpoint.socket", endpoint);

        try (WorkloadApiClient client = DefaultWorkloadApiClient.newClient()) {
            log.debug("Workload API에서 X509Context를 가져오는 중...");
            X509Context context = client.fetchX509Context();

            X509Svid svid = context.getDefaultSvid();
            String spiffeId = svid.getSpiffeId().toString();

            List<X509Certificate> chain = svid.getChain();
            if (chain == null || chain.isEmpty()) {
                throw new IllegalStateException("SVID certificate chain is empty");
            }
            X509Certificate leafCert = chain.get(0);

            Instant notAfter = leafCert.getNotAfter().toInstant();

            log.info("발급 받은 SPIFFE ID = {}", spiffeId);
            log.info("SVID = {}", svid);
            log.info("SVID 만료 시간 = {}", notAfter);


            return new SvidInfo(spiffeId, notAfter);
        } catch (SocketEndpointAddressException e) {
            log.error("Workload API 소켓 주소가 잘못되었습니다. SPIFFE_ENDPOINT_SOCKET 값을 확인하세요.", e);
            throw new IllegalStateException("Invalid SPIFFE_ENDPOINT_SOCKET", e);
        } catch (X509ContextException e) {
            log.error("X509Context를 가져오는 중 오류 발생", e);
            throw new IllegalStateException("Failed to fetch X509Context", e);
        } catch (Exception e) {
            log.error("Workload API 호출 중 예기치 못한 오류 발생", e);
            throw new IllegalStateException("Unexpected error when fetching SVID", e);
        }
    }

}
