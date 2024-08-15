package hello.jdbc.exception.translator;

import hello.jdbc.connection.ConnectionConst;
import hello.jdbc.domain.Member;
import hello.jdbc.repository.MemberRepository;
import hello.jdbc.repository.ex.MyDbException;
import hello.jdbc.repository.ex.MyDuplicateKeyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Random;
import java.util.UUID;

import static hello.jdbc.connection.ConnectionConst.*;

@Slf4j
public class ExTranslatorV1Test {

    Repository repository;
    Service service;

    @BeforeEach
    void init() {
        DataSource dataSource = new DriverManagerDataSource(URL, USERNAME, PASSWORD);
        repository = new Repository(dataSource);
        service = new Service(repository);
    }

    @Test
    void duplicateKeySave() {
        service.create("myId");
        service.create("myId");
    }

    @RequiredArgsConstructor
    static class Service {
        private final Repository repository;

        public void create(String memberId) {
            try {
                repository.save(new Member(memberId, 0));
                log.info("saveId={}", memberId);
            } catch (MyDuplicateKeyException e) {
                log.info("키 중복, 복구 시도");
                String retryId = generateNewId(memberId);
                log.info("retryId={}", retryId);
                repository.save(new Member(retryId, 0));
            } catch (MyDbException e) {
                log.info("데이터 접근 계층 예외", e);
                throw e;
            }
        }

        private String generateNewId(String memberId) {
            return memberId + new Random().nextInt(10000);
        }
    }


    @RequiredArgsConstructor
    static class Repository {
        private final DataSource dataSource;

        public Member save(Member member) {
            String sql = "INSERT INTO member (member_id, money) VALUES (?, ?)";
            Connection con = null;
            PreparedStatement ps = null;

            try {
                con = dataSource.getConnection();
                ps = con.prepareStatement(sql);
                ps.setString(1, member.getMemberId());
                ps.setInt(2, member.getMoney());
                ps.executeUpdate();
                return member;
            } catch (SQLException e) {
                //h2 db
                if (e.getErrorCode() == 23505)
                    throw new MyDuplicateKeyException(e);
                throw new MyDbException(e);
            } finally {
                DataSourceUtils.releaseConnection(con, dataSource);
                JdbcUtils.closeStatement(ps);
                JdbcUtils.closeConnection(con);
            }
        }
    }
}
