package org.folio.tools.store.impl;

import static java.lang.Boolean.TRUE;
import static java.lang.Boolean.parseBoolean;
import static java.util.Objects.nonNull;
import static software.amazon.awssdk.services.ssm.model.ParameterType.SECURE_STRING;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.Security;
import java.util.Properties;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import lombok.extern.log4j.Log4j2;
import org.apache.http.ssl.SSLInitializationException;
import org.apache.http.util.Asserts;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.folio.tools.store.SecureStore;
import org.folio.tools.store.exception.NotFoundException;
import org.folio.tools.store.properties.AwsConfigProperties;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.auth.credentials.SystemPropertyCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.PutParameterRequest;

@Log4j2
public final class AwsStore implements SecureStore {

  public static final String TYPE = "AwsSsm";
  public static final String PROP_REGION = "region";
  public static final String PROP_USE_IAM = "useIAM";
  public static final String PROP_ECS_CREDENTIALS_PATH = "ecsCredentialsPath";
  public static final String PROP_ECS_CREDENTIALS_ENDPOINT = "ecsCredentialsEndpoint";
  public static final String DEFAULT_USE_IAM = "true";
  public static final String ECS_CREDENTIALS_PATH_VAR = "AWS_CONTAINER_CREDENTIALS_RELATIVE_URI";

  private SsmClient ssmClient;

  public AwsStore(Properties properties) {
    ssmClient = buildClient(mapPropertiesToAwsConfigProperties(properties));
  }

  private AwsStore(AwsConfigProperties properties) {
    ssmClient = buildClient(properties);
  }

  @Override
  public String get(String clientId, String tenant, String username) {
    var key = String.format("%s_%s_%s", clientId, tenant, username);
    var request = GetParameterRequest.builder().name(key).withDecryption(true).build();
    return getParameter(request);
  }

  @Override
  public String get(String key) {
    var request = GetParameterRequest.builder().name(key).withDecryption(true).build();
    return getParameter(request);
  }

  @Override
  public void set(String key, String value) {
    var request = PutParameterRequest.builder().name(key).value(value).overwrite(TRUE).type(SECURE_STRING).build();
    ssmClient.putParameter(request);
  }

  public static AwsStore create(AwsConfigProperties properties) {
    return new AwsStore(properties);
  }

  private String getParameter(GetParameterRequest request) {
    try {
      return ssmClient.getParameter(request).parameter().value();
    } catch (Exception e) {
      throw new NotFoundException(e);
    }
  }

  private SsmClient buildClient(AwsConfigProperties properties) {
    log.info("Initializing...");
    var builder = SsmClient.builder();
    builder.region(Region.of(properties.getRegion()));
    if (properties.isFipsEnabled()) {
      builder.httpClient(ApacheHttpClient.builder()
        .tlsTrustManagersProvider(() -> getTrustManager(properties)).build());
      builder.fipsEnabled(true);
    }
    if (nonNull(properties.getUseIam()) && properties.getUseIam()) {
      log.info("Using IAM");
    } else {
      var awsCredentialsProvider = getAwsCredentialsProvider();
      log.info("Using {}", awsCredentialsProvider.getClass().getName());
      builder.credentialsProvider(awsCredentialsProvider);
      builder.endpointOverride(endpoint(properties));
    }
    return builder.build();
  }

  private TrustManager[] getTrustManager(AwsConfigProperties properties) {
    Asserts.notBlank(properties.getTrustStorePath(), "Truststore path must not be blank");
    try {
      Security.addProvider(new BouncyCastleFipsProvider());
      KeyStore trustStore = KeyStore.getInstance(properties.getTrustStoreFileType());

      try (InputStream trustStoreStream = Files.newInputStream(Path.of(properties.getTrustStorePath()))) {
        trustStore.load(trustStoreStream, properties.getTrustStorePassword().toCharArray());
      }
      TrustManagerFactory trustManagerFactory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustManagerFactory.init(trustStore);
      return trustManagerFactory.getTrustManagers();
    } catch (Exception e) {
      throw new SSLInitializationException("Error initializing TrustManager", e);
    }
  }

  private AwsCredentialsProvider getAwsCredentialsProvider() {
    AwsCredentialsProvider credProvider;
    try {
      credProvider = EnvironmentVariableCredentialsProvider.create();
      credProvider.resolveCredentials();
    } catch (Exception e) {
      try {
        credProvider = SystemPropertyCredentialsProvider.create();
        credProvider.resolveCredentials();
      } catch (Exception e2) {
        credProvider = ContainerCredentialsProvider.builder().build();
        credProvider.resolveCredentials();
      }
    }
    return credProvider;
  }

  private AwsConfigProperties mapPropertiesToAwsConfigProperties(Properties properties) {
    return AwsConfigProperties.builder()
      .useIam(parseBoolean(properties.getProperty(PROP_USE_IAM, DEFAULT_USE_IAM)))
      .region(properties.getProperty(PROP_REGION))
      .ecsCredentialsPath(properties.getProperty(PROP_ECS_CREDENTIALS_PATH))
      .ecsCredentialsEndpoint(properties.getProperty(PROP_ECS_CREDENTIALS_ENDPOINT))
      .build();
  }

  private static URI endpoint(AwsConfigProperties properties) {
    var path = properties.getEcsCredentialsPath();
    if (path == null) {
      path = System.getenv(ECS_CREDENTIALS_PATH_VAR);
    }
    if (path == null) {
      throw SdkClientException.create("No credentials path was provided and the environment variable "
        + ECS_CREDENTIALS_PATH_VAR + " is empty");
    }

    try {
      return new URI(properties.getEcsCredentialsEndpoint() + path);
    } catch (URISyntaxException e) {
      throw SdkClientException.builder().cause(e).build();
    }
  }
}
