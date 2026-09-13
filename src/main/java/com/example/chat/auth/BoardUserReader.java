package com.example.chat.auth;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class BoardUserReader {
    private final JdbcClient jdbcClient;
    private final String sql;

    public BoardUserReader(JdbcClient jdbcClient, @Value("${app.board.schema}")String schema){
        this.jdbcClient=jdbcClient;
        this.sql = """
                select u.id, u.email, u.nick_name
                from %1$s.users u
                where u.email = :email
                """.formatted(schema);
    }

    public Optional<ChatUser> findByUsername(String email){
        return jdbcClient.sql(sql)
                .param("email", email)
                .query(
                        (rs, rowNum) -> new ChatUser(rs.getLong("id"),
                        rs.getString("email"),
                        rs.getString("nick_name"))
                ).optional();
    }
}
