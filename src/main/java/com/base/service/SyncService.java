package com.base.service;

import com.base.data.Attendance;
import io.github.cdimascio.dotenv.Dotenv;
import jakarta.annotation.PreDestroy;
import okhttp3.*;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.sql.DataSource;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author YISivlay
 */
@Service
public class SyncService {

    private static final Logger logger = LoggerFactory.getLogger(SyncService.class);
    private final ThreadLocal<String> accessToken = new ThreadLocal<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private final Dotenv env;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public SyncService(final DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.env = Dotenv.configure().directory("./").ignoreIfMissing().load();
        resetAccessToken();
    }

    @Scheduled(cron = "0 */1 * * * ?")
    public void synchronize() {
        logger.info("Starting data synchronization...");
        try {
            synchronization();
        } catch (Exception e) {
            logger.error("Data synchronization failed: ", e);
        }
    }

    private void synchronization() {
        String token = this.accessToken.get();
        List<Attendance> attendances = loadingData();
        if (attendances.isEmpty()) {
            logger.info("No data to sync on client side.");
        } else {
            if (token == null) {
                token = requestClientToken();
                this.accessToken.set(token);
            }
            syncAttendance(attendances, token);
        }
    }

    // TODO config your client side endpoint request here
    private void syncAttendance(final List<Attendance> attendances,
                                final String token) {
        try {
            OkHttpClient client = trustAllClient();
            attendances.forEach(attendance -> {
                String jsonBody = String.format(
                        "{\"fpCode\":\"%s\", \"checkTime\":\"%s\", \"status\":\"%s\"}",
                        attendance.getFpCode(),
                        attendance.getCheckTime(),
                        attendance.getStatus()
                );
                RequestBody requestBody = RequestBody.create(jsonBody, MediaType.get("application/json; charset=utf-8"));
                Request request = new Request.Builder()
                        .url(env.get("CLIENT_BASE_URL") + "/v1/attendance/syncfingerprint")
                        .post(requestBody)
                        .addHeader("content-type", "application/json")
                        .addHeader("Authorization", "bearer " + token)
                        .addHeader("cache-control", "no-cache")
                        .build();
                try {
                    Response response = client.newCall(request).execute();
                    if (response.code() == 200 && response.message().equals("OK")) {
                        logger.info("Successfully synced transaction id {} on client side.", attendance.getId());
                        syncStatus(attendance.getId(), 1);
                    } else if (response.code() == 401 && response.message().equals("Unauthorized")) {
                        logger.error("Endpoint {} unauthorized to authenticate on client side.", request.url());
                    } else {
                        logger.error("Error on client side sync response: {}", response.message());
                        syncStatus(attendance.getId(), 2);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
        }
    }

    private void syncStatus(final Long id,
                            final Integer status) {
        LinkedList<Object> params = new LinkedList<>();

        //TODO check your database from fingerprint device, make sure one table need to replace here
        String sql = "UPDATE `iclock_transaction` SET `status` = ?, `sync_time` = NOW() WHERE `id` = ? ";
        try {
            params.add(status);
            params.add(id);

            Object[] where = params.toArray();
            this.jdbcTemplate.update(sql, where);
        } catch (DataAccessException e) {
            logger.error("Failed to update with id: {} on sync_status = {}", id, status);
        }
    }

    private List<Attendance> loadingData() {

        LinkedList<Object> params = new LinkedList<>();

        //TODO check your database from fingerprint device, make sure one table need to replace here
        String sql = "SELECT t.`id`, t.`code`, t.`status`, " +
                "DATE_FORMAT(t.`check_time`, '%Y-%m-%d %H:%i:%s') check_time " +
                "FROM `iclock_transaction` t " +
                "WHERE t.`status` " + env.get("SYNC_STATUS_OPERATOR", "=") + env.get("SYNC_STATUS", "0") +
                " AND DATE_FORMAT(t.`check_time`, '%Y-%m-%d') >= ? " +
                "ORDER BY DATE_FORMAT(t.`check_time`, '%Y-%m-%d %H:%i:%s') ASC " +
                "LIMIT ? ";

        params.add(env.get("SYNC_START_DATE"));
        params.add(Integer.valueOf(env.get("SYNC_LIMIT_LOAD_RECORD")));

        Object[] where = params.toArray();
        return this.jdbcTemplate.query(sql, (rs, rowNum) -> {

            final Long id = rs.getLong("id");
            final String fpCode = rs.getString("code");
            final String status = rs.getString("status");
            final String checkTime = rs.getString("check_time");

            return new Attendance.Builder()
                    .id(id)
                    .fpCode(fpCode)
                    .status(status)
                    .checkTime(checkTime)
                    .build();
        }, where);
    }

    private String requestClientToken() {
        String accessToken = null;
        try {
            OkHttpClient client = trustAllClient();

            RequestBody requestBody = new FormBody.Builder()
                    .add("username", env.get("CLIENT_USERNAME"))
                    .add("password", env.get("CLIENT_PASSWORD"))
                    .add("client_id", env.get("CLIENT_ID"))
                    .add("client_secret", env.get("CLIENT_SECRET"))
                    .add("grant_type", env.get("CLIENT_GRANT_TYPE"))
                    .build();

            Request request = new Request.Builder()
                    .url(env.get("CLIENT_BASE_URL") + "/oauth/token")
                    .post(requestBody)
                    .addHeader("content-type", "application/x-www-form-urlencoded")
                    .addHeader("cache-control", "no-cache")
                    .build();

            Response response = client.newCall(request).execute();
            if (response.code() == 401 && response.message().equals("Unauthorized")) {
                logger.error("Endpoint {} unauthorized to authenticate on client side.", request.url());
            } else if (response.code() == 200 && response.message().equals("OK")){
                String jsonRes = response.body().string();
                JSONObject r = new JSONObject(jsonRes);
                accessToken = r.getString("access_token");
                logger.info("Successfully fetching token: {}", accessToken);
            } else {
                logger.error("Failed to fetch token: {}", response.message());
            }
        } catch (WebClientResponseException e) {
            logger.error("Failed to fetch token: {}", e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            logger.error("Client side error occurred while fetching token", e);
        }
        return accessToken;
    }

    private void resetAccessToken() {
        scheduler.scheduleAtFixedRate(() -> {
            logger.info("Resetting ThreadLocal accessToken...");
            accessToken.remove();
        }, 10, 10, TimeUnit.MINUTES);
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down ScheduledExecutorService...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }


    private OkHttpClient trustAllClient() throws NoSuchAlgorithmException, KeyManagementException {
        TrustManager[] trustAllCertificates = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(java.security.cert.X509Certificate[] chain,
                                                   String authType) {
                    }

                    @Override
                    public void checkServerTrusted(java.security.cert.X509Certificate[] chain,
                                                   String authType) {
                    }

                    @Override
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[]{};
                    }
                }
        };

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCertificates, new java.security.SecureRandom());

        return new OkHttpClient.Builder()
                .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCertificates[0])
                .hostnameVerifier((hostname, session) -> true)
                .build();
    }
}
