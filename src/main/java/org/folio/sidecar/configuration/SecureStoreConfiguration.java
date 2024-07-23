package org.folio.sidecar.configuration;

import static org.folio.sidecar.utils.ConfigProviderUtils.getRequiredValue;
import static org.folio.sidecar.utils.ConfigProviderUtils.getValue;
import static org.folio.tools.store.properties.VaultConfigProperties.DEFAULT_VAULT_SECRET_ROOT;

import io.quarkus.arc.lookup.LookupIfProperty;
import io.smallrye.config.ConfigMapping;
import jakarta.enterprise.inject.Produces;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.folio.tools.store.SecureStore;
import org.folio.tools.store.impl.AwsStore;
import org.folio.tools.store.impl.EphemeralStore;
import org.folio.tools.store.impl.VaultStore;
import org.folio.tools.store.properties.AwsConfigProperties;
import org.folio.tools.store.properties.EphemeralConfigProperties;
import org.folio.tools.store.properties.VaultConfigProperties;

@Log4j2
public class SecureStoreConfiguration {

  private static final String SECURE_STORE_TYPE_PROP = "secret-store.type";
  private static final String EPHEMERAL_TYPE = "EPHEMERAL";
  private static final String AWS_TYPE = "AWS_SSM";
  private static final String VAULT_TYPE = "VAULT";
  private static final String AWS_PREFIX = "secret-store.aws-ssm.";
  private static final String VAULT_PREFIX = "secret-store.vault.";

  @Produces
  @LookupIfProperty(name = SECURE_STORE_TYPE_PROP, stringValue = EPHEMERAL_TYPE)
  public SecureStore ephemeralStore(EphemeralConfigProperties properties) {
    return EphemeralStore.create(properties);
  }

  @Produces
  @LookupIfProperty(name = SECURE_STORE_TYPE_PROP, stringValue = EPHEMERAL_TYPE)
  public EphemeralConfigProperties ephemeralProperties(EphemeralProperties properties) {
    return new EphemeralConfigProperties(properties.content());
  }

  @Produces
  @LookupIfProperty(name = SECURE_STORE_TYPE_PROP, stringValue = AWS_TYPE)
  public SecureStore awsStore(AwsConfigProperties properties) {
    return AwsStore.create(properties);
  }

  @Produces
  @LookupIfProperty(name = SECURE_STORE_TYPE_PROP, stringValue = AWS_TYPE)
  public AwsConfigProperties awsProperties() {
    return AwsConfigProperties.builder()
      .region(getRequiredValue(AWS_PREFIX, "region"))
      .useIam(getRequiredValue(AWS_PREFIX, "use-iam", Boolean.class))
      .ecsCredentialsPath(getValue(AWS_PREFIX, "ecs-credentials-path"))
      .ecsCredentialsEndpoint(getValue(AWS_PREFIX, "ecs-credentials-endpoint"))
      .fipsEnabled(Boolean.parseBoolean(getValue(AWS_PREFIX, "fips-enabled")))
      .trustStorePath(getValue(AWS_PREFIX, "trust-store-path"))
      .trustStorePassword(getValue(AWS_PREFIX, "trust-store-password"))
      .trustStoreFileType(getValue(AWS_PREFIX, "trust-store-file-type"))
      .build();
  }

  @Produces
  @LookupIfProperty(name = SECURE_STORE_TYPE_PROP, stringValue = VAULT_TYPE)
  public SecureStore vaultStore(VaultConfigProperties properties) {
    return VaultStore.create(properties);
  }

  @Produces
  @LookupIfProperty(name = SECURE_STORE_TYPE_PROP, stringValue = VAULT_TYPE)
  public VaultConfigProperties vaultProperties() {
    return VaultConfigProperties.builder()
      .token(getRequiredValue(VAULT_PREFIX, "token"))
      .address(getRequiredValue(VAULT_PREFIX, "address"))
      .enableSsl(getRequiredValue(VAULT_PREFIX, "enable-ssl", Boolean.class))
      .pemFilePath(getValue(VAULT_PREFIX, "pem-file-path"))
      .keystorePassword(getValue(VAULT_PREFIX, "keystore-password"))
      .keystoreFilePath(getValue(VAULT_PREFIX, "keystore-file-path"))
      .truststoreFilePath(getValue(VAULT_PREFIX, "truststore-file-path"))
      .secretRoot(DEFAULT_VAULT_SECRET_ROOT)
      .build();
  }

  @ConfigMapping(prefix = "secret-store.ephemeral")
  @LookupIfProperty(name = SECURE_STORE_TYPE_PROP, stringValue = EPHEMERAL_TYPE)
  private interface EphemeralProperties {

    Map<String, String> content();
  }
}
