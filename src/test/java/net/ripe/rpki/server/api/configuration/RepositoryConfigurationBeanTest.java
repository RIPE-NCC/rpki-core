package net.ripe.rpki.server.api.configuration;

import com.google.common.base.VerifyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RepositoryConfigurationBeanTest {
    private RepositoryConfigurationBean subject;

    @Test
    public void testValidatesHashType(@TempDir Path baseDirectory) {
        final Function<String, RepositoryConfigurationBean> configWithHash = (hash) -> new RepositoryConfigurationBean(
                "https://rrdp.example.org/notification.xml",
                "rsync://rpki.example.org/repository/",
                baseDirectory.resolve("repository").toString(),
                "rsync://rpki.example.org/ta/",
                baseDirectory.resolve("ta").toString(),
                "CN=ALL Resources,O=RIPE NCC,C=NL",
                "CN=RIPE NCC Resources,O=RIPE NCC,C=NL",
                hash
        );


        assertThatThrownBy(() -> configWithHash.apply(null))
                .isInstanceOf(VerifyException.class);
        assertThatThrownBy(() -> configWithHash.apply("this_is_not_a_bcrypt_hash"))
                .isInstanceOf(IllegalArgumentException.class);

        // bcrypt.using(rounds=14).hash(plaintext)
        final RepositoryConfigurationBean config = configWithHash.apply("$2b$14$LKU8uOslBN7Y8.29tIuq7.yNQq6w1LpfvYtC5OQRiGcVC4LPfPnYW");

        assertThat(RepositoryConfigurationBean.checkAdminPassword("plaintext")).isTrue();
        assertThat(RepositoryConfigurationBean.checkAdminPassword("othertext")).isFalse();
    }
}
