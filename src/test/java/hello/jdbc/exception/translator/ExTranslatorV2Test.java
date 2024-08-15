package hello.jdbc.exception.translator;

import hello.jdbc.domain.Member;
import hello.jdbc.repository.ex.MyDbException;
import hello.jdbc.repository.ex.MyDuplicateKeyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Random;

import static hello.jdbc.connection.ConnectionConst.*;

@Slf4j
public class ExTranslatorV2Test {

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
            } catch (DuplicateKeyException e) {
                log.info("키 중복, 복구 시도", e);
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
                SQLErrorCodeSQLExceptionTranslator exTranslator = new SQLErrorCodeSQLExceptionTranslator(dataSource);
                DataAccessException resultEx = exTranslator.translate("save", sql, e);
                if (resultEx != null && resultEx.getClass() == DuplicateKeyException.class) {
                    throw resultEx;
                }
                throw new MyDbException(e);
            } finally {
                DataSourceUtils.releaseConnection(con, dataSource);
                JdbcUtils.closeStatement(ps);
                JdbcUtils.closeConnection(con);
            }
        }
    }
}
