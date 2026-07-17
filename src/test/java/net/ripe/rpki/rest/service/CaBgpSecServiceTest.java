package net.ripe.rpki.rest.service;

import lombok.extern.slf4j.Slf4j;
import net.ripe.ipresource.Asn;
import net.ripe.rpki.TestRpkiBootApplication;
import net.ripe.rpki.commons.util.VersionedId;
import net.ripe.rpki.domain.bgpsec.Csr;
import net.ripe.rpki.domain.bgpsec.RouterId;
import net.ripe.rpki.server.api.commands.CreateBgpSecConfigurationCommand;
import net.ripe.rpki.server.api.commands.DeleteBgpSecConfigurationCommand;
import net.ripe.rpki.server.api.dto.BgpSecConfigurationData;
import net.ripe.rpki.server.api.dto.HostedCertificateAuthorityData;
import net.ripe.rpki.server.api.services.command.CommandService;
import net.ripe.rpki.server.api.services.read.BgpSecViewService;
import net.ripe.rpki.server.api.services.read.CertificateAuthorityViewService;
import net.ripe.rpki.services.impl.handlers.BgpSecConfigurationCommandHandlerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import javax.security.auth.x500.X500Principal;
import java.util.List;
import java.util.Optional;

import static net.ripe.rpki.rest.service.RestService.API_URL_PREFIX;
import static net.ripe.rpki.services.impl.handlers.BgpSecConfigurationCommandHandlerTest.CSR;
import static net.ripe.rpki.services.impl.handlers.BgpSecConfigurationCommandHandlerTest.CSR2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Slf4j
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@AutoConfigureMockMvc
@AutoConfigureWebMvc
@SpringBootTest(classes = TestRpkiBootApplication.class)
class CaBgpSecServiceTest {

    private static final long CA_ID = 123L;

    @MockitoBean
    private BgpSecViewService bgpSecViewService;

    @MockitoBean
    private CertificateAuthorityViewService certificateAuthorityViewService;

    private final HostedCertificateAuthorityData certificateAuthorityData = mock(HostedCertificateAuthorityData.class);

    @MockitoBean
    private CommandService commandService;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(certificateAuthorityViewService.findCertificateAuthorityByName(any(X500Principal.class))).thenReturn(certificateAuthorityData);
        when(certificateAuthorityData.getId()).thenReturn(CA_ID);
        when(certificateAuthorityData.getVersionedId()).thenReturn(new VersionedId(CA_ID));
    }

    @Nested
    class ListRouterKeys {
        @Test
        void shouldListAllRouterKeys() throws Exception {
            when(bgpSecViewService.findBgpSecConfiguration(CA_ID)).thenReturn(
                    List.of(
                            BgpSecConfigurationData.from(1L, new Asn(20), RouterId.ZERO, CSR),
                            BgpSecConfigurationData.from(2L, new Asn(30), new RouterId(4_000_000_000L), BgpSecConfigurationCommandHandlerTest.CSR2)));

            mockMvc.perform(Rest.get(API_URL_PREFIX + "/" + CA_ID + "/bgpsec"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.routerKeys.length()").value("2"))
                    .andExpect(jsonPath("$.routerKeys.[0].routerKeyId").value(1))
                    .andExpect(jsonPath("$.routerKeys.[0].asn").value("AS20"))
                    .andExpect(jsonPath("$.routerKeys.[0].routerId").value(0))
                    .andExpect(jsonPath("$.routerKeys.[0].csr").value(CSR))
                    .andExpect(jsonPath("$.routerKeys.[0].keyIdentifier").value("17316903F0671229E8808BA8E8AB0105FA915A07"))
                    .andExpect(jsonPath("$.routerKeys.[1].routerKeyId").value(2))
                    .andExpect(jsonPath("$.routerKeys.[1].asn").value("AS30"))
                    .andExpect(jsonPath("$.routerKeys.[1].routerId").value(4_000_000_000L))
                    .andExpect(jsonPath("$.routerKeys.[1].csr").value(CSR2))
                    .andExpect(jsonPath("$.routerKeys.[1].keyIdentifier").value(Csr.getKeyIdentifier(CSR2)));
        }

        @Test
        void shouldFilterByKeyIdentifier() throws Exception {
            when(bgpSecViewService.findBgpSecConfiguration(CA_ID)).thenReturn(
                    List.of(
                            BgpSecConfigurationData.from(1L, new Asn(20), RouterId.ZERO, CSR),
                            BgpSecConfigurationData.from(2L, new Asn(30), new RouterId(4_000_000_000L), BgpSecConfigurationCommandHandlerTest.CSR2)));

            String key = Csr.getKeyIdentifier(CSR2);
            mockMvc.perform(Rest.get(API_URL_PREFIX + "/123/bgpsec?keyIdentifier=" + key))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.routerKeys.length()").value("1"))
                    .andExpect(jsonPath("$.routerKeys.[0].asn").value("AS30"))
                    .andExpect(jsonPath("$.routerKeys.[0].keyIdentifier").value(key));
        }

        @Test
        void shouldFilterByAsnAndRouterId() throws Exception {
            when(bgpSecViewService.findBgpSecConfiguration(CA_ID)).thenReturn(
                    List.of(
                            BgpSecConfigurationData.from(1L, new Asn(20), RouterId.ZERO, CSR),
                            BgpSecConfigurationData.from(2L, new Asn(30), new RouterId(4_000_000_000L), BgpSecConfigurationCommandHandlerTest.CSR2)));

            mockMvc.perform(Rest.get(API_URL_PREFIX + "/123/bgpsec?asn=AS30&routerId=4000000000"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.routerKeys.length()").value("1"))
                    .andExpect(jsonPath("$.routerKeys.[0].asn").value("AS30"))
                    .andExpect(jsonPath("$.routerKeys.[0].routerId").value("4000000000"))
                    .andExpect(jsonPath("$.routerKeys.[0].keyIdentifier").value(Csr.getKeyIdentifier(CSR2)));
        }

        @Test
        void shouldRejectInvalidAsn() throws Exception {
            mockMvc.perform(Rest.get(API_URL_PREFIX + "/123/bgpsec?asn=INVALID"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value(containsString("Invalid ASN value")));
        }

        @Test
        void shouldRejectNegativeRouterId() throws Exception {
            mockMvc.perform(Rest.get(API_URL_PREFIX + "/123/bgpsec?routerId=-1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value(containsString("Router ID must be a positive integer")));
        }
    }

    @Nested
    class CreateRouterKey {
        @Test
        void shouldCreateRouterKey() throws Exception {
            when(bgpSecViewService.findBgpSecConfiguration(CA_ID)).thenReturn(List.of(
                    BgpSecConfigurationData.from(42L, new Asn(10), new RouterId(10L), CSR)
            ));

            mockMvc.perform(Rest.post(API_URL_PREFIX + "/" + CA_ID + "/bgpsec")
                            .content("{\"asn\": \"AS10\", \"routerId\": 10, \"csr\": \"" + CSR + "\" }")
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.routerKeyId").value(42))
                    .andExpect(jsonPath("$.asn").value("AS10"))
                    .andExpect(jsonPath("$.routerId").value(10))
                    .andExpect(jsonPath("$.keyIdentifier").value("17316903F0671229E8808BA8E8AB0105FA915A07"))
                    .andExpect(jsonPath("$.csr").value(CSR));

            ArgumentCaptor<CreateBgpSecConfigurationCommand> captor = ArgumentCaptor.forClass(CreateBgpSecConfigurationCommand.class);
            verify(commandService).execute(captor.capture());
            var command = captor.getValue();
            assertThat(command).isEqualTo(
                    new CreateBgpSecConfigurationCommand(certificateAuthorityData.getVersionedId(), Asn.parse("AS10"), new RouterId(10L), CSR)
            );
        }

        @Test
        void shouldRejectInvalidAsn() throws Exception {
            mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/bgpsec")
                            .content("{ \"asn\": \"xxxzzz\", \"routerId\": 0, \"csr\": \"" + CSR + "\" }")
                    )
                    .andExpect(status().is(BAD_REQUEST.value()))
                    .andExpect(jsonPath("$.error").value(equalTo("not a legal ASN: xxxzzz")));
        }

        @Test
        void shouldRejectMissingCSR() throws Exception {
            mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/bgpsec")
                            .content("{ \"asn\": \"AS1\", \"routerId\": 0 }")
                    )
                    .andExpect(status().is(BAD_REQUEST.value()))
                    .andExpect(jsonPath("$.error").value(equalTo("CSR is required")));
        }

        @Test
        void shouldRejectInvalidRouterId() throws Exception {
            mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/bgpsec")
                            .content("{ \"asn\": \"AS10\", \"routerId\": -1, \"csr\": \"" + CSR + "\" }")
                    )
                    .andExpect(status().is(BAD_REQUEST.value()))
                    .andExpect(jsonPath("$.error").value(equalTo("Router ID must be a positive integer, actual: -1")));
        }

        @Test
        void shouldRejectInvalidRouterIdType() throws Exception {
            mockMvc.perform(Rest.post(API_URL_PREFIX + "/123/bgpsec")
                    .content("{ \"asn\": \"AS10\", \"routerId\": \"<router-id>\", \"csr\": \"" + CSR + "\" }")
                )
                .andExpect(status().is(BAD_REQUEST.value()))
                .andExpect(jsonPath("$.error").value(equalTo("Router ID must be a positive integer")));
        }
    }

    @Nested
    class RouterKey {
        final BgpSecConfigurationData routerKey = BgpSecConfigurationData.from(
                42L,
                new Asn(20),
                RouterId.ZERO,
                CSR
        );
        final String url = API_URL_PREFIX + "/" + CA_ID + "/bgpsec/" + routerKey.id();

        @BeforeEach
        void setup() {
            when(bgpSecViewService.findBgpSecConfigurationById(CA_ID, routerKey.id()))
                    .thenReturn(Optional.of(routerKey));
        }

        @Nested
        class Get {
            @Test
            void shouldShowRouterKey() throws Exception {
                mockMvc.perform(Rest.get(url))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.routerKeyId").value(routerKey.id()))
                        .andExpect(jsonPath("$.asn").value(routerKey.asn().toString()))
                        .andExpect(jsonPath("$.routerId").value(routerKey.routerId().value()))
                        .andExpect(jsonPath("$.keyIdentifier").value(routerKey.keyIdentifier()))
                        .andExpect(jsonPath("$.csr").value(routerKey.csr()));
            }

            @Test
            void shouldReturnNotFoundForNonExistingRouterKey() throws Exception {
                mockMvc.perform(Rest.get(API_URL_PREFIX + "/123/bgpsec/0"))
                        .andExpect(status().isNotFound());
            }
        }

        @Nested
        class Certificate {
            @Test
            void shouldGetEeCertificateAsAsn1EncodedPkcs7() throws Exception {
                when(bgpSecViewService.findBgpSecConfigurationById(CA_ID, 1L)).thenReturn(
                        Optional.of(BgpSecConfigurationData.from(1L, new Asn(20), RouterId.ZERO, CSR)));
                byte[] fakeDer = new byte[]{0x30, 0x00};
                when(bgpSecViewService.findBgpSecCertificatePkcs7(eq(CA_ID), any(BgpSecConfigurationData.class)))
                        .thenReturn(Optional.of(fakeDer));

                mockMvc.perform(Rest.get(url + "/certificate"))
                        .andExpect(status().isOk())
                        .andExpect(header().string("content-type", containsString("application/pkcs7-mime")))
                        .andExpect(header().string("content-disposition", containsString("bgpsec-cert.p7c")))
                        .andExpect(content().bytes(fakeDer));
            }

            @Test
            void shouldReturnNotFoundForNonExistingRouterKey() throws Exception {
                mockMvc.perform(Rest.get(API_URL_PREFIX + "/123/bgpsec/0/certificate"))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.error").value(equalTo("BGPSec configuration not found.")));
            }

            @Test
            void shouldReturnNotFoundForUnissuedRouterKey() throws Exception {
                mockMvc.perform(Rest.get(url + "/certificate"))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.error").value(equalTo("The BGPSec configuration exists but the certificate has not yet been issued.")));
            }
        }

        @Nested
        class Chain {
            @Test
            void shouldReturnChainAsDerPkcs7() throws Exception {
                when(bgpSecViewService.findBgpSecConfiguration(CA_ID)).thenReturn(
                        List.of(BgpSecConfigurationData.from(1L, new Asn(20), RouterId.ZERO, CSR)));
                byte[] fakeDer = new byte[]{0x30, 0x00};
                when(bgpSecViewService.findBgpSecCertificateChainPkcs7(anyLong(), any(BgpSecConfigurationData.class)))
                        .thenReturn(Optional.of(fakeDer));

                mockMvc.perform(Rest.get(url + "/chain"))
                        .andExpect(status().isOk())
                        .andExpect(header().string("content-type", containsString("application/pkcs7-mime")))
                        .andExpect(header().string("content-disposition", containsString("bgpsec-chain.p7c")))
                        .andExpect(content().bytes(fakeDer));
            }

            @Test
            void shouldReturnNotFoundForNonExistingRouterKey() throws Exception {
                mockMvc.perform(Rest.get(API_URL_PREFIX + "/123/bgpsec/0/chain"))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.error").value(equalTo("BGPSec configuration not found.")));
            }

            @Test
            void shouldReturnNotFoundForUnissuedRouterKey() throws Exception {
                mockMvc.perform(Rest.get(url + "/chain"))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.error").value(equalTo("The BGPSec configuration exists but the certificate has not yet been issued.")));
            }
        }

        @Nested
        class Revoke {
            @Test
            void shouldRevokeRouterKey() throws Exception {
                when(bgpSecViewService.findBgpSecConfigurationById(CA_ID, routerKey.id()))
                        .thenReturn(Optional.of(routerKey));

                mockMvc.perform(Rest.delete(url)).andExpect(status().isNoContent());

                ArgumentCaptor<DeleteBgpSecConfigurationCommand> captor = ArgumentCaptor.forClass(DeleteBgpSecConfigurationCommand.class);
                verify(commandService).execute(captor.capture());
                assertThat(captor.getValue().getRemoved()).isEqualTo(routerKey);
            }

            @Test
            void shouldReturnNotFoundForNonExistingRouterKey() throws Exception {
                mockMvc.perform(Rest.delete(API_URL_PREFIX + "/" + CA_ID + "/bgpsec/0"))
                        .andExpect(status().isNoContent());
            }
        }
    }
}
