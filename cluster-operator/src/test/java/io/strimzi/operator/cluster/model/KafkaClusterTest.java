/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.cluster.model;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapKeySelectorBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.HostAlias;
import io.fabric8.kubernetes.api.model.HostAliasBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.LabelSelectorRequirementBuilder;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PodSecurityContext;
import io.fabric8.kubernetes.api.model.PodSecurityContextBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecurityContext;
import io.fabric8.kubernetes.api.model.SecurityContextBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.TopologySpreadConstraint;
import io.fabric8.kubernetes.api.model.TopologySpreadConstraintBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.WeightedPodAffinityTerm;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicyIngressRule;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicyPeer;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicyPeerBuilder;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.openshift.api.model.Route;
import io.strimzi.api.kafka.model.CertSecretSource;
import io.strimzi.api.kafka.model.CertSecretSourceBuilder;
import io.strimzi.api.kafka.model.CertificateExpirationPolicy;
import io.strimzi.api.kafka.model.ContainerEnvVar;
import io.strimzi.api.kafka.model.CruiseControlResources;
import io.strimzi.api.kafka.model.GenericSecretSourceBuilder;
import io.strimzi.api.kafka.model.InlineLogging;
import io.strimzi.api.kafka.model.JmxPrometheusExporterMetrics;
import io.strimzi.api.kafka.model.JmxPrometheusExporterMetricsBuilder;
import io.strimzi.api.kafka.model.Kafka;
import io.strimzi.api.kafka.model.KafkaAuthorizationKeycloakBuilder;
import io.strimzi.api.kafka.model.KafkaBuilder;
import io.strimzi.api.kafka.model.KafkaExporterResources;
import io.strimzi.api.kafka.model.KafkaJmxAuthenticationPasswordBuilder;
import io.strimzi.api.kafka.model.KafkaJmxOptionsBuilder;
import io.strimzi.api.kafka.model.KafkaResources;
import io.strimzi.api.kafka.model.MetricsConfig;
import io.strimzi.api.kafka.model.Rack;
import io.strimzi.api.kafka.model.RackBuilder;
import io.strimzi.api.kafka.model.SystemProperty;
import io.strimzi.api.kafka.model.SystemPropertyBuilder;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthenticationCustomBuilder;
import io.strimzi.api.kafka.model.listener.KafkaListenerAuthenticationOAuthBuilder;
import io.strimzi.api.kafka.model.listener.NodeAddressType;
import io.strimzi.api.kafka.model.listener.arraylistener.GenericKafkaListenerBuilder;
import io.strimzi.api.kafka.model.listener.arraylistener.GenericKafkaListenerConfigurationBootstrap;
import io.strimzi.api.kafka.model.listener.arraylistener.GenericKafkaListenerConfigurationBootstrapBuilder;
import io.strimzi.api.kafka.model.listener.arraylistener.GenericKafkaListenerConfigurationBroker;
import io.strimzi.api.kafka.model.listener.arraylistener.GenericKafkaListenerConfigurationBrokerBuilder;
import io.strimzi.api.kafka.model.listener.arraylistener.KafkaListenerType;
import io.strimzi.api.kafka.model.storage.EphemeralStorageBuilder;
import io.strimzi.api.kafka.model.storage.JbodStorageBuilder;
import io.strimzi.api.kafka.model.storage.PersistentClaimStorageBuilder;
import io.strimzi.api.kafka.model.storage.PersistentClaimStorageOverrideBuilder;
import io.strimzi.api.kafka.model.storage.Storage;
import io.strimzi.api.kafka.model.template.ContainerTemplate;
import io.strimzi.api.kafka.model.template.ExternalTrafficPolicy;
import io.strimzi.api.kafka.model.template.IpFamily;
import io.strimzi.api.kafka.model.template.IpFamilyPolicy;
import io.strimzi.api.kafka.model.template.PodManagementPolicy;
import io.strimzi.certs.OpenSslCertManager;
import io.strimzi.operator.PlatformFeaturesAvailability;
import io.strimzi.operator.cluster.KafkaVersionTestUtils;
import io.strimzi.operator.cluster.ResourceUtils;
import io.strimzi.operator.cluster.operator.resource.cruisecontrol.CruiseControlConfigurationParameters;
import io.strimzi.operator.common.MetricsAndLogging;
import io.strimzi.operator.common.PasswordGenerator;
import io.strimzi.operator.common.Reconciliation;
import io.strimzi.operator.common.Util;
import io.strimzi.operator.common.model.Labels;
import io.strimzi.platform.KubernetesVersion;
import io.strimzi.plugin.security.profiles.impl.RestrictedPodSecurityProvider;
import io.strimzi.test.TestUtils;
import io.strimzi.test.annotations.ParallelSuite;
import io.strimzi.test.annotations.ParallelTest;
import org.apache.kafka.common.Uuid;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;

import java.io.IOException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.strimzi.test.TestUtils.set;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings({
    "checkstyle:ClassDataAbstractionCoupling",
    "checkstyle:ClassFanOutComplexity",
    "checkstyle:JavaNCSS"
})
@ParallelSuite
public class KafkaClusterTest {
    private static final KafkaVersion.Lookup VERSIONS = KafkaVersionTestUtils.getKafkaVersionLookup();
    private final String namespace = "test";
    private final String cluster = "foo";
    private final int replicas = 3;
    private final String image = "image";
    private final int healthDelay = 120;
    private final int healthTimeout = 30;
    private final Map<String, Object> metricsCm = singletonMap("animal", "wombat");
    private final String metricsCmJson = "{\"animal\":\"wombat\"}";
    private final String metricsCMName = "metrics-cm";
    private final ConfigMap metricsCM = io.strimzi.operator.cluster.TestUtils.getJmxMetricsCm(metricsCmJson, metricsCMName, "metrics-config.yml");
    private final JmxPrometheusExporterMetrics jmxMetricsConfig = io.strimzi.operator.cluster.TestUtils.getJmxPrometheusExporterMetrics("metrics-config.yml", metricsCMName);
    private final Map<String, Object> configuration = singletonMap("foo", "bar");
    private final InlineLogging kafkaLog = new InlineLogging();
    private final InlineLogging zooLog = new InlineLogging();
    {
        kafkaLog.setLoggers(Collections.singletonMap("kafka.root.logger.level", "OFF"));
        zooLog.setLoggers(Collections.singletonMap("zookeeper.root.logger", "OFF"));
    }
    private final List<SystemProperty> javaSystemProperties = List.of(
            new SystemPropertyBuilder().withName("javax.net.debug").withValue("verbose").build(),
            new SystemPropertyBuilder().withName("something.else").withValue("42").build()
    );

    private final Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas, image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, kafkaLog, zooLog))
            .editSpec()
                .editKafka()
                    .withNewJvmOptions()
                        .addAllToJavaSystemProperties(javaSystemProperties)
                    .endJvmOptions()
                .endKafka()
            .endSpec()
            .build();

    private final KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

    private void checkOwnerReference(OwnerReference ownerRef, HasMetadata resource)  {
        assertThat(resource.getMetadata().getOwnerReferences().size(), is(1));
        assertThat(resource.getMetadata().getOwnerReferences().get(0), is(ownerRef));
    }

    @ParallelTest
    public void testMetricsConfigMap() {
        ConfigMap metricsCm = kc.generateMetricsAndLogConfigMap(new MetricsAndLogging(metricsCM, null));
        checkMetricsConfigMap(metricsCm);
        checkOwnerReference(kc.createOwnerReference(), metricsCm);
    }

    @ParallelTest
    public void  testJavaSystemProperties() {
        assertThat(kc.getEnvVars().get(3).getName(), is("STRIMZI_JAVA_SYSTEM_PROPERTIES"));
        assertThat(kc.getEnvVars().get(3).getValue(), is("-D" + javaSystemProperties.get(0).getName() + "=" + javaSystemProperties.get(0).getValue() + " " +
                "-D" + javaSystemProperties.get(1).getName() + "=" + javaSystemProperties.get(1).getValue()));
    }

    private void checkMetricsConfigMap(ConfigMap metricsCm) {
        assertThat(metricsCm.getData().get(AbstractModel.ANCILLARY_CM_KEY_METRICS), is(TestUtils.toJsonString(this.metricsCm)));
    }

    private Map<String, String> expectedLabels()    {
        return TestUtils.map(
            Labels.STRIMZI_CLUSTER_LABEL, cluster,
            "my-user-label", "cromulent",
            Labels.STRIMZI_NAME_LABEL, KafkaResources.kafkaStatefulSetName(cluster),
            Labels.STRIMZI_KIND_LABEL, Kafka.RESOURCE_KIND,
            Labels.KUBERNETES_NAME_LABEL, KafkaCluster.APPLICATION_NAME,
            Labels.KUBERNETES_INSTANCE_LABEL, this.cluster,
            Labels.KUBERNETES_PART_OF_LABEL, Labels.APPLICATION_NAME + "-" + this.cluster,
            Labels.KUBERNETES_MANAGED_BY_LABEL, AbstractModel.STRIMZI_CLUSTER_OPERATOR_NAME);
    }

    private Map<String, String> expectedSelectorLabels()    {
        return Labels.fromMap(expectedLabels()).strimziSelectorLabels().toMap();
    }

    @ParallelTest
    public void testGenerateService() {
        Service headful = kc.generateService();

        assertThat(headful.getSpec().getType(), is("ClusterIP"));
        assertThat(headful.getSpec().getSelector(), is(expectedSelectorLabels()));
        assertThat(headful.getSpec().getPorts().size(), is(3));
        assertThat(headful.getSpec().getPorts().get(0).getName(), is(KafkaCluster.REPLICATION_PORT_NAME));
        assertThat(headful.getSpec().getPorts().get(0).getPort(), is(KafkaCluster.REPLICATION_PORT));
        assertThat(headful.getSpec().getPorts().get(0).getProtocol(), is("TCP"));
        assertThat(headful.getSpec().getPorts().get(1).getName(), is(ListenersUtils.BACKWARDS_COMPATIBLE_PLAIN_PORT_NAME));
        assertThat(headful.getSpec().getPorts().get(1).getPort(), is(9092));
        assertThat(headful.getSpec().getPorts().get(1).getProtocol(), is("TCP"));
        assertThat(headful.getSpec().getPorts().get(2).getName(), is(ListenersUtils.BACKWARDS_COMPATIBLE_TLS_PORT_NAME));
        assertThat(headful.getSpec().getPorts().get(2).getPort(), is(9093));
        assertThat(headful.getSpec().getPorts().get(2).getProtocol(), is("TCP"));
        assertThat(headful.getSpec().getIpFamilyPolicy(), is(nullValue()));
        assertThat(headful.getSpec().getIpFamilies(), is(emptyList()));

        assertThat(headful.getMetadata().getAnnotations(), is(Util.mergeLabelsOrAnnotations(kc.getInternalDiscoveryAnnotation())));

        assertThat(headful.getMetadata().getLabels().containsKey(Labels.STRIMZI_DISCOVERY_LABEL), is(true));
        assertThat(headful.getMetadata().getLabels().get(Labels.STRIMZI_DISCOVERY_LABEL), is("true"));

        checkOwnerReference(kc.createOwnerReference(), headful);
    }

    @ParallelTest
    public void testGenerateServiceWithoutMetrics() {
        Kafka kafka = new KafkaBuilder(kafkaAssembly)
                .editSpec()
                    .editKafka()
                        .withMetricsConfig(null)
                    .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafka, VERSIONS);
        Service headful = kc.generateService();

        assertThat(headful.getSpec().getType(), is("ClusterIP"));
        assertThat(headful.getSpec().getSelector(), is(expectedSelectorLabels()));
        assertThat(headful.getSpec().getPorts().size(), is(3));
        assertThat(headful.getSpec().getPorts().get(0).getName(), is(KafkaCluster.REPLICATION_PORT_NAME));
        assertThat(headful.getSpec().getPorts().get(0).getPort(), is(KafkaCluster.REPLICATION_PORT));
        assertThat(headful.getSpec().getPorts().get(0).getProtocol(), is("TCP"));
        assertThat(headful.getSpec().getPorts().get(1).getName(), is(ListenersUtils.BACKWARDS_COMPATIBLE_PLAIN_PORT_NAME));
        assertThat(headful.getSpec().getPorts().get(1).getPort(), is(9092));
        assertThat(headful.getSpec().getPorts().get(1).getProtocol(), is("TCP"));
        assertThat(headful.getSpec().getPorts().get(2).getName(), is(ListenersUtils.BACKWARDS_COMPATIBLE_TLS_PORT_NAME));
        assertThat(headful.getSpec().getPorts().get(2).getPort(), is(9093));
        assertThat(headful.getSpec().getPorts().get(2).getProtocol(), is("TCP"));

        assertThat(headful.getMetadata().getAnnotations().containsKey("prometheus.io/port"), is(false));
        assertThat(headful.getMetadata().getAnnotations().containsKey("prometheus.io/scrape"), is(false));
        assertThat(headful.getMetadata().getAnnotations().containsKey("prometheus.io/path"), is(false));

        assertThat(headful.getMetadata().getLabels().containsKey(Labels.STRIMZI_DISCOVERY_LABEL), is(true));
        assertThat(headful.getMetadata().getLabels().get(Labels.STRIMZI_DISCOVERY_LABEL), is("true"));

        checkOwnerReference(kc.createOwnerReference(), headful);
    }

    @ParallelTest
    public void testGenerateHeadlessServiceWithJmxMetrics() {
        Kafka kafka = new KafkaBuilder(kafkaAssembly)
                .editSpec()
                    .editKafka()
                        .withJmxOptions(new KafkaJmxOptionsBuilder().build())
                    .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafka, VERSIONS);
        Service headless = kc.generateHeadlessService();

        assertThat(headless.getSpec().getType(), is("ClusterIP"));
        assertThat(headless.getSpec().getSelector(), is(expectedSelectorLabels()));
        assertThat(headless.getSpec().getPorts().size(), is(5));
        assertThat(headless.getSpec().getPorts().get(0).getName(), is(KafkaCluster.CONTROLPLANE_PORT_NAME));
        assertThat(headless.getSpec().getPorts().get(0).getPort(), is(KafkaCluster.CONTROLPLANE_PORT));
        assertThat(headless.getSpec().getPorts().get(0).getProtocol(), is("TCP"));
        assertThat(headless.getSpec().getPorts().get(1).getName(), is(KafkaCluster.REPLICATION_PORT_NAME));
        assertThat(headless.getSpec().getPorts().get(1).getPort(), is(KafkaCluster.REPLICATION_PORT));
        assertThat(headless.getSpec().getPorts().get(1).getProtocol(), is("TCP"));
        assertThat(headless.getSpec().getPorts().get(2).getName(), is(ListenersUtils.BACKWARDS_COMPATIBLE_PLAIN_PORT_NAME));
        assertThat(headless.getSpec().getPorts().get(2).getPort(), is(9092));
        assertThat(headless.getSpec().getPorts().get(2).getProtocol(), is("TCP"));
        assertThat(headless.getSpec().getPorts().get(3).getName(), is(ListenersUtils.BACKWARDS_COMPATIBLE_TLS_PORT_NAME));
        assertThat(headless.getSpec().getPorts().get(3).getPort(), is(9093));
        assertThat(headless.getSpec().getPorts().get(3).getProtocol(), is("TCP"));
        assertThat(headless.getSpec().getPorts().get(4).getName(), is(KafkaCluster.JMX_PORT_NAME));
        assertThat(headless.getSpec().getPorts().get(4).getPort(), is(KafkaCluster.JMX_PORT));
        assertThat(headless.getSpec().getPorts().get(4).getProtocol(), is("TCP"));

        assertThat(headless.getMetadata().getLabels().containsKey(Labels.STRIMZI_DISCOVERY_LABEL), is(false));

        checkOwnerReference(kc.createOwnerReference(), headless);
    }

    @ParallelTest
    public void testJmxSecretCustomLabelsAndAnnotations() {
        Map<String, String> customLabels = new HashMap<>(2);
        customLabels.put("label1", "value1");
        customLabels.put("label2", "value2");

        Map<String, String> customAnnotations = new HashMap<>(2);
        customAnnotations.put("anno1", "value3");
        customAnnotations.put("anno2", "value4");

        Kafka kafka = new KafkaBuilder(kafkaAssembly)
                .editSpec()
                    .editKafka()
                        .withJmxOptions(new KafkaJmxOptionsBuilder()
                            .withAuthentication(new KafkaJmxAuthenticationPasswordBuilder()
                                  .build())
                            .build())
                        .withNewTemplate()
                            .withNewJmxSecret()
                                .withNewMetadata()
                                    .withAnnotations(customAnnotations)
                                    .withLabels(customLabels)
                                .endMetadata()
                            .endJmxSecret()
                        .endTemplate()
                    .endKafka()
                .endSpec()
                .build();

        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafka, VERSIONS);
        Secret jmxSecret = kc.generateJmxSecret(null);

        for (Map.Entry<String, String> entry : customAnnotations.entrySet()) {
            assertThat(jmxSecret.getMetadata().getAnnotations(), hasEntry(entry.getKey(), entry.getValue()));
        }
        for (Map.Entry<String, String> entry : customLabels.entrySet()) {
            assertThat(jmxSecret.getMetadata().getLabels(), hasEntry(entry.getKey(), entry.getValue()));
        }
    }

    @ParallelTest
    public void testJmxSecret() {
        Kafka kafka = new KafkaBuilder(kafkaAssembly)
                .editSpec()
                    .editKafka()
                        .withJmxOptions(new KafkaJmxOptionsBuilder()
                            .withAuthentication(new KafkaJmxAuthenticationPasswordBuilder()
                                  .build())
                            .build())
                    .endKafka()
                .endSpec()
                .build();

        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafka, VERSIONS);
        Secret jmxSecret = kc.generateJmxSecret(null);

        assertThat(jmxSecret.getData(), hasKey("jmx-username"));
        assertThat(jmxSecret.getData(), hasKey("jmx-password"));

        Secret newJmxSecret = kc.generateJmxSecret(jmxSecret);

        assertThat(newJmxSecret.getData(), hasKey("jmx-username"));
        assertThat(newJmxSecret.getData(), hasKey("jmx-password"));
        assertThat(newJmxSecret.getData().get("jmx-username"), is(jmxSecret.getData().get("jmx-username")));
        assertThat(newJmxSecret.getData().get("jmx-password"), is(jmxSecret.getData().get("jmx-password")));
    }

    @ParallelTest
    public void testGenerateHeadlessService() {
        Service headless = kc.generateHeadlessService();
        checkHeadlessService(headless);
        checkOwnerReference(kc.createOwnerReference(), headless);
    }

    private void checkHeadlessService(Service headless) {
        assertThat(headless.getMetadata().getName(), is(KafkaResources.brokersServiceName(cluster)));
        assertThat(headless.getSpec().getType(), is("ClusterIP"));
        assertThat(headless.getSpec().getClusterIP(), is("None"));
        assertThat(headless.getSpec().getSelector(), is(expectedSelectorLabels()));
        assertThat(headless.getSpec().getPorts().size(), is(4));
        assertThat(headless.getSpec().getPorts().get(0).getName(), is(KafkaCluster.CONTROLPLANE_PORT_NAME));
        assertThat(headless.getSpec().getPorts().get(0).getPort(), is(KafkaCluster.CONTROLPLANE_PORT));
        assertThat(headless.getSpec().getPorts().get(0).getProtocol(), is("TCP"));
        assertThat(headless.getSpec().getPorts().get(1).getName(), is(KafkaCluster.REPLICATION_PORT_NAME));
        assertThat(headless.getSpec().getPorts().get(1).getPort(), is(KafkaCluster.REPLICATION_PORT));
        assertThat(headless.getSpec().getPorts().get(1).getProtocol(), is("TCP"));
        assertThat(headless.getSpec().getPorts().get(2).getName(), is(ListenersUtils.BACKWARDS_COMPATIBLE_PLAIN_PORT_NAME));
        assertThat(headless.getSpec().getPorts().get(2).getPort(), is(9092));
        assertThat(headless.getSpec().getPorts().get(2).getProtocol(), is("TCP"));
        assertThat(headless.getSpec().getPorts().get(3).getName(), is(ListenersUtils.BACKWARDS_COMPATIBLE_TLS_PORT_NAME));
        assertThat(headless.getSpec().getPorts().get(3).getPort(), is(9093));
        assertThat(headless.getSpec().getPorts().get(3).getProtocol(), is("TCP"));
        assertThat(headless.getSpec().getIpFamilyPolicy(), is(nullValue()));
        assertThat(headless.getSpec().getIpFamilies(), is(emptyList()));

        assertThat(headless.getMetadata().getLabels().containsKey(Labels.STRIMZI_DISCOVERY_LABEL), is(false));
    }

    @ParallelTest
    public void testPerBrokerConfiguration() {
        String config = kc.generateSharedBrokerConfiguration(true);

        assertThat(config, CoreMatchers.containsString("broker.id=${STRIMZI_BROKER_ID}"));
        assertThat(config, CoreMatchers.containsString("node.id=${STRIMZI_BROKER_ID}"));
        assertThat(config, CoreMatchers.containsString("log.dirs=/var/lib/kafka/data/kafka-log${STRIMZI_BROKER_ID}"));
        assertThat(config, CoreMatchers.containsString("advertised.listeners=CONTROLPLANE-9090://foo-kafka-${STRIMZI_BROKER_ID}.foo-kafka-brokers.test.svc:9090,REPLICATION-9091://foo-kafka-${STRIMZI_BROKER_ID}.foo-kafka-brokers.test.svc:9091,PLAIN-9092://${STRIMZI_PLAIN_9092_ADVERTISED_HOSTNAME}:${STRIMZI_PLAIN_9092_ADVERTISED_PORT},TLS-9093://${STRIMZI_TLS_9093_ADVERTISED_HOSTNAME}:${STRIMZI_TLS_9093_ADVERTISED_PORT}\n"));
    }

    @ParallelTest
    public void testPerBrokerConfigMaps() {
        MetricsAndLogging metricsAndLogging = new MetricsAndLogging(metricsCM, null);
        Map<Integer, Map<String, String>> advertisedHostnames = Map.of(
                0, Map.of("PLAIN_9092", "broker-0", "TLS_9093", "broker-0"),
                1, Map.of("PLAIN_9092", "broker-1", "TLS_9093", "broker-1"),
                2, Map.of("PLAIN_9092", "broker-2", "TLS_9093", "broker-2")
        );
        Map<Integer, Map<String, String>> advertisedPorts = Map.of(
                0, Map.of("PLAIN_9092", "10000", "TLS_9093", "20000"),
                1, Map.of("PLAIN_9092", "10001", "TLS_9093", "20001"),
                2, Map.of("PLAIN_9092", "10002", "TLS_9093", "20002")
        );

        ConfigMap cm = kc.generateSharedConfigurationConfigMap(metricsAndLogging, advertisedHostnames, advertisedPorts, true);

        assertThat(cm.getData().size(), is(6));
        assertThat(cm.getMetadata().getName(), is("foo-kafka-config"));
        assertThat(cm.getData().get("metrics-config.json"), is(notNullValue()));
        assertThat(cm.getData().get("log4j.properties"), is(notNullValue()));
        assertThat(cm.getData().get("server.config"), is(notNullValue()));
        assertThat(cm.getData().get("listeners.config"), is("PLAIN_9092 TLS_9093"));
        assertThat(cm.getData().get("advertised-hostnames.config"), is("PLAIN_9092_0://broker-0 TLS_9093_0://broker-0 PLAIN_9092_1://broker-1 TLS_9093_1://broker-1 PLAIN_9092_2://broker-2 TLS_9093_2://broker-2"));
        assertThat(cm.getData().get("advertised-ports.config"), is("PLAIN_9092_0://10000 TLS_9093_0://20000 PLAIN_9092_1://10001 TLS_9093_1://20001 PLAIN_9092_2://10002 TLS_9093_2://20002"));
    }

    @ParallelTest
    public void testGenerateStatefulSet() {
        // We expect a single statefulSet ...
        StatefulSet sts = kc.generateStatefulSet(true, null, null, null);
        checkStatefulSet(sts, kafkaAssembly);
        checkOwnerReference(kc.createOwnerReference(), sts);

        // Check Volumes
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().size(), is(7));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().get(0).getName(), is("data"));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().get(0).getEmptyDir(), is(notNullValue()));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().get(1).getName(), is(AbstractModel.STRIMZI_TMP_DIRECTORY_DEFAULT_VOLUME_NAME));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().get(1).getEmptyDir(), is(notNullValue()));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().get(2).getName(), is(KafkaCluster.CLUSTER_CA_CERTS_VOLUME));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().get(2).getSecret().getSecretName(), is("foo-cluster-ca-cert"));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().get(3).getName(), is(KafkaCluster.BROKER_CERTS_VOLUME));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().get(3).getSecret().getSecretName(), is("foo-kafka-brokers"));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().get(4).getName(), is(KafkaCluster.CLIENT_CA_CERTS_VOLUME));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().get(4).getSecret().getSecretName(), is("foo-clients-ca-cert"));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().get(5).getName(), is("kafka-metrics-and-logging"));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().get(5).getConfigMap().getName(), is("foo-kafka-config"));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().get(6).getName(), is("ready-files"));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().get(6).getEmptyDir(), is(notNullValue()));
    }

    @ParallelTest
    public void testGenerateStatefulSetWithSetStorageSelector() {
        Map<String, String> selector = TestUtils.map("foo", "bar");
        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                .editKafka()
                .withNewPersistentClaimStorage().withSelector(selector).withSize("100Gi").endPersistentClaimStorage()
                .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);
        StatefulSet sts = kc.generateStatefulSet(false, null, null, null);
        assertThat(sts.getSpec().getVolumeClaimTemplates().get(0).getSpec().getSelector().getMatchLabels(), is(selector));
    }

    @ParallelTest
    public void testGenerateStatefulSetWithEmptyStorageSelector() {
        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                .editKafka()
                .withNewPersistentClaimStorage().withSelector(emptyMap()).withSize("100Gi").endPersistentClaimStorage()
                .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);
        StatefulSet sts = kc.generateStatefulSet(false, null, null, null);
        assertThat(sts.getSpec().getVolumeClaimTemplates().get(0).getSpec().getSelector(), is(nullValue()));
    }

    @ParallelTest
    public void testGenerateStatefulSetWithSetSizeLimit() {
        String sizeLimit = "1Gi";
        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withNewEphemeralStorage().withSizeLimit(sizeLimit).endEphemeralStorage()
                    .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);
        StatefulSet sts = kc.generateStatefulSet(false, null, null, null);
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().get(0).getEmptyDir().getSizeLimit(), is(new Quantity("1", "Gi")));
    }

    @ParallelTest
    public void testGenerateStatefulSetWithEmptySizeLimit() {
        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withNewEphemeralStorage().endEphemeralStorage()
                    .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);
        StatefulSet sts = kc.generateStatefulSet(false, null, null, null);
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().get(0).getEmptyDir().getSizeLimit(), is(nullValue()));
    }

    @ParallelTest
    public void testGenerateStatefulSetWithRack() {
        Kafka editKafkaAssembly = new KafkaBuilder(kafkaAssembly)
                .editSpec()
                    .editKafka()
                        .withNewRack().withTopologyKey("rack-key").endRack()
                    .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, editKafkaAssembly, VERSIONS);
        StatefulSet sts = kc.generateStatefulSet(true, null, null, null);
        checkStatefulSet(sts, editKafkaAssembly);
    }

    @ParallelTest
    public void testGenerateStatefulSetWithInitContainers() {
        Kafka editKafkaAssembly =
                new KafkaBuilder(kafkaAssembly)
                        .editSpec()
                            .editKafka()
                                .withNewPersistentClaimStorage().withSize("1Gi").endPersistentClaimStorage()
                                .withNewRack().withTopologyKey("rack-key").endRack()
                            .endKafka()
                        .endSpec().build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, editKafkaAssembly, VERSIONS);
        StatefulSet sts = kc.generateStatefulSet(false, null, null, null);
        checkStatefulSet(sts, editKafkaAssembly);
    }

    @ParallelTest
    public void testGenerateStatefulSetWithPodManagementPolicy() {
        Kafka editKafkaAssembly =
                new KafkaBuilder(kafkaAssembly)
                        .editSpec()
                            .editKafka()
                                .withNewTemplate()
                                    .withNewStatefulset()
                                        .withPodManagementPolicy(PodManagementPolicy.ORDERED_READY)
                                    .endStatefulset()
                                .endTemplate()
                            .endKafka()
                        .endSpec().build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, editKafkaAssembly, VERSIONS);
        StatefulSet sts = kc.generateStatefulSet(false, null, null, null);
        assertThat(sts.getSpec().getPodManagementPolicy(), is(PodManagementPolicy.ORDERED_READY.toValue()));
    }

    private void checkStatefulSet(StatefulSet sts, Kafka cm) {
        assertThat(sts.getMetadata().getName(), is(KafkaResources.kafkaStatefulSetName(cluster)));
        // ... in the same namespace ...
        assertThat(sts.getMetadata().getNamespace(), is(namespace));
        // ... with these labels
        assertThat(sts.getMetadata().getLabels(), is(expectedLabels()));
        assertThat(sts.getSpec().getSelector().getMatchLabels(), is(expectedSelectorLabels()));

        assertThat(sts.getSpec().getTemplate().getSpec().getSchedulerName(), is("default-scheduler"));

        List<Container> containers = sts.getSpec().getTemplate().getSpec().getContainers();

        assertThat(containers.size(), is(1));

        // checks on the main Kafka container
        assertThat(sts.getSpec().getReplicas(), is(replicas));
        assertThat(sts.getSpec().getPodManagementPolicy(), is(PodManagementPolicy.PARALLEL.toValue()));
        assertThat(containers.get(0).getImage(), is(image));
        assertThat(containers.get(0).getLivenessProbe().getTimeoutSeconds(), is(healthTimeout));
        assertThat(containers.get(0).getLivenessProbe().getInitialDelaySeconds(), is(healthDelay));
        assertThat(containers.get(0).getLivenessProbe().getFailureThreshold(), is(10));
        assertThat(containers.get(0).getLivenessProbe().getSuccessThreshold(), is(4));
        assertThat(containers.get(0).getLivenessProbe().getPeriodSeconds(), is(33));
        assertThat(containers.get(0).getReadinessProbe().getTimeoutSeconds(), is(healthTimeout));
        assertThat(containers.get(0).getReadinessProbe().getInitialDelaySeconds(), is(healthDelay));
        assertThat(containers.get(0).getReadinessProbe().getFailureThreshold(), is(10));
        assertThat(containers.get(0).getReadinessProbe().getSuccessThreshold(), is(4));
        assertThat(containers.get(0).getReadinessProbe().getPeriodSeconds(), is(33));
        assertThat(AbstractModel.containerEnvVars(containers.get(0)).get(KafkaCluster.ENV_VAR_STRIMZI_KAFKA_GC_LOG_ENABLED), is(Boolean.toString(AbstractModel.DEFAULT_JVM_GC_LOGGING_ENABLED)));
        assertThat(containers.get(0).getVolumeMounts().get(1).getName(), is(AbstractModel.STRIMZI_TMP_DIRECTORY_DEFAULT_VOLUME_NAME));
        assertThat(containers.get(0).getVolumeMounts().get(1).getMountPath(), is(AbstractModel.STRIMZI_TMP_DIRECTORY_DEFAULT_MOUNT_PATH));
        assertThat(containers.get(0).getVolumeMounts().get(3).getName(), is(KafkaCluster.BROKER_CERTS_VOLUME));
        assertThat(containers.get(0).getVolumeMounts().get(3).getMountPath(), is(KafkaCluster.BROKER_CERTS_VOLUME_MOUNT));
        assertThat(containers.get(0).getVolumeMounts().get(2).getName(), is(KafkaCluster.CLUSTER_CA_CERTS_VOLUME));
        assertThat(containers.get(0).getVolumeMounts().get(2).getMountPath(), is(KafkaCluster.CLUSTER_CA_CERTS_VOLUME_MOUNT));
        assertThat(containers.get(0).getVolumeMounts().get(4).getName(), is(KafkaCluster.CLIENT_CA_CERTS_VOLUME));
        assertThat(containers.get(0).getVolumeMounts().get(4).getMountPath(), is(KafkaCluster.CLIENT_CA_CERTS_VOLUME_MOUNT));
        assertThat(containers.get(0).getPorts().get(0).getName(), is(KafkaCluster.CONTROLPLANE_PORT_NAME));
        assertThat(containers.get(0).getPorts().get(0).getContainerPort(), is(KafkaCluster.CONTROLPLANE_PORT));
        assertThat(containers.get(0).getPorts().get(0).getProtocol(), is("TCP"));
        assertThat(containers.get(0).getPorts().get(1).getName(), is(KafkaCluster.REPLICATION_PORT_NAME));
        assertThat(containers.get(0).getPorts().get(1).getContainerPort(), is(KafkaCluster.REPLICATION_PORT));
        assertThat(containers.get(0).getPorts().get(1).getProtocol(), is("TCP"));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream()
            .filter(volume -> volume.getName().equalsIgnoreCase("strimzi-tmp"))
            .findFirst().orElseThrow().getEmptyDir().getSizeLimit(), is(new Quantity(AbstractModel.STRIMZI_TMP_DIRECTORY_DEFAULT_SIZE)));

        if (cm.getSpec().getKafka().getRack() != null) {

            Rack rack = cm.getSpec().getKafka().getRack();

            // check that the pod spec contains anti-affinity rules with the right topology key
            PodSpec podSpec = sts.getSpec().getTemplate().getSpec();
            assertThat(podSpec.getAffinity(), is(notNullValue()));
            assertThat(podSpec.getAffinity().getPodAntiAffinity(), is(notNullValue()));
            assertThat(podSpec.getAffinity().getPodAntiAffinity().getPreferredDuringSchedulingIgnoredDuringExecution(), is(notNullValue()));
            List<WeightedPodAffinityTerm> terms = podSpec.getAffinity().getPodAntiAffinity().getPreferredDuringSchedulingIgnoredDuringExecution();
            assertThat(terms, is(notNullValue()));
            assertThat(terms.size() > 0, is(true));

            boolean isTopologyKey =
                    terms.stream().anyMatch(term -> term.getPodAffinityTerm().getTopologyKey().equals(rack.getTopologyKey()));
            assertThat(isTopologyKey, is(true));

            // check that pod spec contains the init Kafka container
            List<Container> initContainers = podSpec.getInitContainers();
            assertThat(initContainers, is(notNullValue()));
            assertThat(initContainers.size() > 0, is(true));

            boolean isInitKafka =
                    initContainers.stream().anyMatch(container -> container.getName().equals(KafkaCluster.INIT_NAME));
            assertThat(isInitKafka, is(true));
        }
    }

    // TODO test volume claim templates

    @ParallelTest
    public void testPodNames() {

        for (int i = 0; i < replicas; i++) {
            assertThat(kc.getPodName(i), is(KafkaResources.kafkaStatefulSetName(cluster) + "-" + i));
        }
    }

    @ParallelTest
    public void testPvcNames() {
        Kafka assembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withStorage(new PersistentClaimStorageBuilder().withDeleteClaim(false).withSize("100Gi").build())
                    .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, assembly, VERSIONS);

        List<PersistentVolumeClaim> pvcs = kc.getPersistentVolumeClaimTemplates();

        for (int i = 0; i < replicas; i++) {
            assertThat(pvcs.get(0).getMetadata().getName() + "-" + KafkaResources.kafkaPodName(cluster, i),
                    is(KafkaCluster.VOLUME_NAME + "-" + KafkaResources.kafkaPodName(cluster, i)));
        }

        assembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withStorage(new JbodStorageBuilder().withVolumes(
                            new PersistentClaimStorageBuilder().withDeleteClaim(false).withId(0).withSize("100Gi").build(),
                            new PersistentClaimStorageBuilder().withDeleteClaim(true).withId(1).withSize("100Gi").build())
                            .build())
                    .endKafka()
                .endSpec()
                .build();
        kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, assembly, VERSIONS);

        pvcs = kc.getPersistentVolumeClaimTemplates();

        for (int i = 0; i < replicas; i++) {
            int id = 0;
            for (PersistentVolumeClaim pvc : pvcs) {
                assertThat(pvc.getMetadata().getName() + "-" + KafkaResources.kafkaPodName(cluster, i),
                        is(KafkaCluster.VOLUME_NAME + "-" + id++ + "-" + KafkaResources.kafkaPodName(cluster, i)));
            }
        }
    }

    @ParallelTest
    public void withAffinityWithoutRack() throws IOException {
        ResourceTester<Kafka, KafkaCluster> resourceTester = new ResourceTester<>(Kafka.class, VERSIONS, (kafkaAssembly1, versions) -> KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly1, versions), this.getClass().getSimpleName() + ".withAffinityWithoutRack");
        resourceTester.assertDesiredResource("-STS.yaml",
            kc -> kc.generateStatefulSet(true, null, null, null).getSpec().getTemplate().getSpec().getAffinity());
    }

    @ParallelTest
    public void withRackWithoutAffinity() throws IOException {
        ResourceTester<Kafka, KafkaCluster> resourceTester = new ResourceTester<>(Kafka.class, VERSIONS, (kafkaAssembly1, versions) -> KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly1, versions), this.getClass().getSimpleName() + ".withRackWithoutAffinity");
        resourceTester.assertDesiredResource("-STS.yaml",
            kc -> kc.generateStatefulSet(true, null, null, null).getSpec().getTemplate().getSpec().getAffinity());
    }

    @ParallelTest
    public void withRackAndAffinityWithMoreTerms() throws IOException {
        ResourceTester<Kafka, KafkaCluster> resourceTester = new ResourceTester<>(Kafka.class, VERSIONS, (kafkaAssembly1, versions) -> KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly1, versions), this.getClass().getSimpleName() + ".withRackAndAffinityWithMoreTerms");
        resourceTester.assertDesiredResource("-STS.yaml",
            kc -> kc.generateStatefulSet(true, null, null, null).getSpec().getTemplate().getSpec().getAffinity());
    }

    @ParallelTest
    public void withRackAndAffinity() throws IOException {
        ResourceTester<Kafka, KafkaCluster> resourceTester = new ResourceTester<>(Kafka.class, VERSIONS, (kafkaAssembly1, versions) -> KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly1, versions), this.getClass().getSimpleName() + ".withRackAndAffinity");
        resourceTester.assertDesiredResource("-STS.yaml",
            kc -> kc.generateStatefulSet(true, null, null, null).getSpec().getTemplate().getSpec().getAffinity());
    }

    @ParallelTest
    public void withTolerations() throws IOException {
        ResourceTester<Kafka, KafkaCluster> resourceTester = new ResourceTester<>(Kafka.class, VERSIONS, (kafkaAssembly1, versions) -> KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly1, versions), this.getClass().getSimpleName() + ".withTolerations");
        resourceTester.assertDesiredResource("-STS.yaml",
            kc -> kc.generateStatefulSet(true, null, null, null).getSpec().getTemplate().getSpec().getTolerations());
    }

    @ParallelTest
    public void testExternalRoutes() {
        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                .editKafka()
                .withListeners(new GenericKafkaListenerBuilder()
                        .withName("external")
                        .withPort(9094)
                        .withType(KafkaListenerType.ROUTE)
                        .withTls(true)
                        .withNewKafkaListenerAuthenticationTlsAuth()
                        .endKafkaListenerAuthenticationTlsAuth()
                        .build())
                .endKafka()
                .endSpec()
                .build();

        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        // Check StatefulSet changes
        StatefulSet sts = kc.generateStatefulSet(true, null, null, null);

        List<ContainerPort> ports = sts.getSpec().getTemplate().getSpec().getContainers().get(0).getPorts();
        assertThat(ports.contains(kc.createContainerPort(ListenersUtils.BACKWARDS_COMPATIBLE_EXTERNAL_PORT_NAME, 9094, "TCP")), is(true));

        // Check external bootstrap service
        Service ext = kc.generateExternalBootstrapServices().get(0);
        assertThat(ext.getMetadata().getName(), is(KafkaResources.externalBootstrapServiceName(cluster)));
        assertThat(ext.getSpec().getType(), is("ClusterIP"));
        assertThat(ext.getSpec().getSelector(), is(kc.getSelectorLabels().toMap()));
        assertThat(ext.getSpec().getPorts(), is(Collections.singletonList(kc.createServicePort(ListenersUtils.BACKWARDS_COMPATIBLE_EXTERNAL_PORT_NAME, 9094, 9094, "TCP"))));
        checkOwnerReference(kc.createOwnerReference(), ext);

        // Check per pod services
        for (int i = 0; i < replicas; i++)  {
            Service srv = kc.generateExternalServices(i).get(0);
            assertThat(srv.getMetadata().getName(), is(KafkaResources.kafkaStatefulSetName(cluster) + "-" + i));
            assertThat(srv.getSpec().getType(), is("ClusterIP"));
            assertThat(srv.getSpec().getSelector().get(Labels.KUBERNETES_STATEFULSET_POD_LABEL), is(KafkaResources.kafkaPodName(cluster, i)));
            assertThat(srv.getSpec().getPorts(), is(Collections.singletonList(kc.createServicePort(ListenersUtils.BACKWARDS_COMPATIBLE_EXTERNAL_PORT_NAME, 9094, 9094, "TCP"))));
            checkOwnerReference(kc.createOwnerReference(), srv);
        }

        // Check bootstrap route
        Route brt = kc.generateExternalBootstrapRoutes().get(0);
        assertThat(brt.getMetadata().getName(), is(KafkaResources.bootstrapServiceName(cluster)));
        assertThat(brt.getSpec().getTls().getTermination(), is("passthrough"));
        assertThat(brt.getSpec().getTo().getKind(), is("Service"));
        assertThat(brt.getSpec().getTo().getName(), is(KafkaResources.externalBootstrapServiceName(cluster)));
        assertThat(brt.getSpec().getPort().getTargetPort(), is(new IntOrString(9094)));
        checkOwnerReference(kc.createOwnerReference(), brt);

        // Check per pod router
        for (int i = 0; i < replicas; i++)  {
            Route rt = kc.generateExternalRoutes(i).get(0);
            assertThat(rt.getMetadata().getName(), is(KafkaResources.kafkaStatefulSetName(cluster) + "-" + i));
            assertThat(rt.getSpec().getTls().getTermination(), is("passthrough"));
            assertThat(rt.getSpec().getTo().getKind(), is("Service"));
            assertThat(rt.getSpec().getTo().getName(), is(KafkaResources.kafkaStatefulSetName(cluster) + "-" + i));
            assertThat(rt.getSpec().getPort().getTargetPort(), is(new IntOrString(9094)));
            checkOwnerReference(kc.createOwnerReference(), rt);
        }
    }

    @ParallelTest
    public void testExternalRoutesWithHostOverrides() {
        GenericKafkaListenerConfigurationBroker routeListenerBrokerConfig0 = new GenericKafkaListenerConfigurationBroker();
        routeListenerBrokerConfig0.setBroker(0);
        routeListenerBrokerConfig0.setHost("my-host-0.cz");

        GenericKafkaListenerConfigurationBroker routeListenerBrokerConfig1 = new GenericKafkaListenerConfigurationBroker();
        routeListenerBrokerConfig1.setBroker(1);
        routeListenerBrokerConfig1.setHost("my-host-1.cz");

        GenericKafkaListenerConfigurationBroker routeListenerBrokerConfig2 = new GenericKafkaListenerConfigurationBroker();
        routeListenerBrokerConfig2.setBroker(2);
        routeListenerBrokerConfig2.setHost("my-host-2.cz");

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withListeners(new GenericKafkaListenerBuilder()
                                .withName("external")
                                .withPort(9094)
                                .withType(KafkaListenerType.ROUTE)
                                .withTls(true)
                                .withNewKafkaListenerAuthenticationTlsAuth()
                                .endKafkaListenerAuthenticationTlsAuth()
                                .withNewConfiguration()
                                    .withNewBootstrap()
                                        .withHost("my-boostrap.cz")
                                    .endBootstrap()
                                    .withBrokers(routeListenerBrokerConfig0, routeListenerBrokerConfig1, routeListenerBrokerConfig2)
                                .endConfiguration()
                                .build())
                    .endKafka()
                .endSpec()
                .build();

        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        // Check bootstrap route
        Route brt = kc.generateExternalBootstrapRoutes().get(0);
        assertThat(brt.getMetadata().getName(), is(KafkaResources.bootstrapServiceName(cluster)));
        assertThat(brt.getSpec().getHost(), is("my-boostrap.cz"));

        // Check per pod router
        for (int i = 0; i < replicas; i++)  {
            Route rt = kc.generateExternalRoutes(i).get(0);
            assertThat(rt.getMetadata().getName(), is(KafkaResources.kafkaStatefulSetName(cluster) + "-" + i));
            assertThat(rt.getSpec().getHost(), is("my-host-" + i + ".cz"));
        }
    }

    @ParallelTest
    public void testExternalRoutesWithLabelsAndAnnotations() {
        GenericKafkaListenerConfigurationBroker routeListenerBrokerConfig0 = new GenericKafkaListenerConfigurationBroker();
        routeListenerBrokerConfig0.setBroker(0);
        routeListenerBrokerConfig0.setAnnotations(Collections.singletonMap("anno", "anno-value-0"));
        routeListenerBrokerConfig0.setLabels(Collections.singletonMap("label", "label-value-0"));

        GenericKafkaListenerConfigurationBroker routeListenerBrokerConfig1 = new GenericKafkaListenerConfigurationBroker();
        routeListenerBrokerConfig1.setBroker(1);
        routeListenerBrokerConfig1.setAnnotations(Collections.singletonMap("anno", "anno-value-1"));
        routeListenerBrokerConfig1.setLabels(Collections.singletonMap("label", "label-value-1"));

        GenericKafkaListenerConfigurationBroker routeListenerBrokerConfig2 = new GenericKafkaListenerConfigurationBroker();
        routeListenerBrokerConfig2.setBroker(2);
        routeListenerBrokerConfig2.setAnnotations(Collections.singletonMap("anno", "anno-value-2"));
        routeListenerBrokerConfig2.setLabels(Collections.singletonMap("label", "label-value-2"));

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withListeners(new GenericKafkaListenerBuilder()
                                .withName("external")
                                .withPort(9094)
                                .withType(KafkaListenerType.ROUTE)
                                .withTls(true)
                                .withNewKafkaListenerAuthenticationTlsAuth()
                                .endKafkaListenerAuthenticationTlsAuth()
                                .withNewConfiguration()
                                    .withNewBootstrap()
                                        .withAnnotations(Collections.singletonMap("anno", "anno-value"))
                                        .withLabels(Collections.singletonMap("label", "label-value"))
                                    .endBootstrap()
                                    .withBrokers(routeListenerBrokerConfig0, routeListenerBrokerConfig1, routeListenerBrokerConfig2)
                                .endConfiguration()
                                .build())
                    .endKafka()
                .endSpec()
                .build();

        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        // Check bootstrap route
        Route brt = kc.generateExternalBootstrapRoutes().get(0);
        assertThat(brt.getMetadata().getName(), is(KafkaResources.bootstrapServiceName(cluster)));
        assertThat(brt.getMetadata().getAnnotations().get("anno"), is("anno-value"));
        assertThat(brt.getMetadata().getLabels().get("label"), is("label-value"));

        // Check per pod router
        for (int i = 0; i < replicas; i++)  {
            Route rt = kc.generateExternalRoutes(i).get(0);
            assertThat(rt.getMetadata().getName(), is(KafkaResources.kafkaStatefulSetName(cluster) + "-" + i));
            assertThat(rt.getMetadata().getAnnotations().get("anno"), is("anno-value-" + i));
            assertThat(rt.getMetadata().getLabels().get("label"), is("label-value-" + i));
        }
    }

    @ParallelTest
    public void testExternalLoadBalancers() {
        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withListeners(new GenericKafkaListenerBuilder()
                                .withName("external")
                                .withPort(9094)
                                .withType(KafkaListenerType.LOADBALANCER)
                                .withTls(true)
                                .withNewKafkaListenerAuthenticationTlsAuth()
                                .endKafkaListenerAuthenticationTlsAuth()
                                .build())
                    .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        // Check StatefulSet changes
        StatefulSet sts = kc.generateStatefulSet(true, null, null, null);

        List<ContainerPort> ports = sts.getSpec().getTemplate().getSpec().getContainers().get(0).getPorts();
        assertThat(ports.contains(kc.createContainerPort(ListenersUtils.BACKWARDS_COMPATIBLE_EXTERNAL_PORT_NAME, 9094, "TCP")), is(true));

        // Check external bootstrap service
        Service ext = kc.generateExternalBootstrapServices().get(0);
        assertThat(ext.getMetadata().getName(), is(KafkaResources.externalBootstrapServiceName(cluster)));
        assertThat(ext.getMetadata().getFinalizers(), is(emptyList()));
        assertThat(ext.getSpec().getType(), is("LoadBalancer"));
        assertThat(ext.getSpec().getSelector(), is(kc.getSelectorLabels().toMap()));
        assertThat(ext.getSpec().getPorts(), is(Collections.singletonList(kc.createServicePort(ListenersUtils.BACKWARDS_COMPATIBLE_EXTERNAL_PORT_NAME, 9094, 9094, "TCP"))));
        assertThat(ext.getSpec().getLoadBalancerIP(), is(nullValue()));
        assertThat(ext.getSpec().getExternalTrafficPolicy(), is("Cluster"));
        assertThat(ext.getSpec().getLoadBalancerSourceRanges(), is(emptyList()));
        checkOwnerReference(kc.createOwnerReference(), ext);

        // Check per pod services
        for (int i = 0; i < replicas; i++)  {
            Service srv = kc.generateExternalServices(i).get(0);
            assertThat(srv.getMetadata().getName(), is(KafkaResources.kafkaStatefulSetName(cluster) + "-" + i));
            assertThat(srv.getMetadata().getFinalizers(), is(emptyList()));
            assertThat(srv.getSpec().getType(), is("LoadBalancer"));
            assertThat(srv.getSpec().getSelector().get(Labels.KUBERNETES_STATEFULSET_POD_LABEL), is(KafkaResources.kafkaPodName(cluster, i)));
            assertThat(srv.getSpec().getPorts(), is(Collections.singletonList(kc.createServicePort(ListenersUtils.BACKWARDS_COMPATIBLE_EXTERNAL_PORT_NAME, 9094, 9094, "TCP"))));
            assertThat(srv.getSpec().getLoadBalancerIP(), is(nullValue()));
            assertThat(srv.getSpec().getExternalTrafficPolicy(), is("Cluster"));
            assertThat(srv.getSpec().getLoadBalancerSourceRanges(), is(emptyList()));
            checkOwnerReference(kc.createOwnerReference(), srv);
        }
    }

    @ParallelTest
    public void testExternalLoadBalancersWithoutBootstrapService() {
        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                .editKafka()
                .withListeners(new GenericKafkaListenerBuilder()
                        .withName("external")
                        .withPort(9094)
                        .withType(KafkaListenerType.LOADBALANCER)
                        .withTls(true)
                        .withNewKafkaListenerAuthenticationTlsAuth()
                        .endKafkaListenerAuthenticationTlsAuth()
                        .withNewConfiguration()
                            .withCreateBootstrapService(false)
                        .endConfiguration()
                        .build())
                .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        // Check external bootstrap service
        assertThat(kc.generateExternalBootstrapServices().isEmpty(), is(true));
    }

    @ParallelTest
    public void testLoadBalancerExternalTrafficPolicyLocalFromListener() {
        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withListeners(new GenericKafkaListenerBuilder()
                                .withName("external")
                                .withPort(9094)
                                .withType(KafkaListenerType.LOADBALANCER)
                                .withTls(true)
                                .withNewConfiguration()
                                    .withExternalTrafficPolicy(ExternalTrafficPolicy.LOCAL)
                                .endConfiguration()
                                .build())
                    .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        // Check external bootstrap service
        Service ext = kc.generateExternalBootstrapServices().get(0);
        assertThat(ext.getSpec().getExternalTrafficPolicy(), is(ExternalTrafficPolicy.LOCAL.toValue()));

        // Check per pod services
        for (int i = 0; i < replicas; i++)  {
            Service srv = kc.generateExternalServices(i).get(0);
            assertThat(srv.getSpec().getExternalTrafficPolicy(), is(ExternalTrafficPolicy.LOCAL.toValue()));
        }
    }

    @ParallelTest
    public void testLoadBalancerExternalTrafficPolicyClusterFromListener() {
        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withListeners(new GenericKafkaListenerBuilder()
                                .withName("external")
                                .withPort(9094)
                                .withType(KafkaListenerType.LOADBALANCER)
                                .withTls(true)
                                .withNewConfiguration()
                                    .withExternalTrafficPolicy(ExternalTrafficPolicy.CLUSTER)
                                .endConfiguration()
                                .build())
                    .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        // Check external bootstrap service
        Service ext = kc.generateExternalBootstrapServices().get(0);
        assertThat(ext.getSpec().getExternalTrafficPolicy(), is(ExternalTrafficPolicy.CLUSTER.toValue()));

        // Check per pod services
        for (int i = 0; i < replicas; i++)  {
            Service srv = kc.generateExternalServices(i).get(0);
            assertThat(srv.getSpec().getExternalTrafficPolicy(), is(ExternalTrafficPolicy.CLUSTER.toValue()));
        }
    }

    @ParallelTest
    public void testFinalizersFromListener() {
        List<String> finalizers = List.of("service.kubernetes.io/load-balancer-cleanup", "mydomain.io/my-custom-finalizer");

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withListeners(new GenericKafkaListenerBuilder()
                                .withName("external")
                                .withPort(9094)
                                .withType(KafkaListenerType.LOADBALANCER)
                                .withTls(true)
                                .withNewConfiguration()
                                    .withFinalizers(finalizers)
                                .endConfiguration()
                                .build())
                    .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        // Check external bootstrap service
        Service ext = kc.generateExternalBootstrapServices().get(0);
        assertThat(ext.getMetadata().getFinalizers(), is(finalizers));

        // Check per pod services
        for (int i = 0; i < replicas; i++)  {
            Service srv = kc.generateExternalServices(i).get(0);
            assertThat(srv.getMetadata().getFinalizers(), is(finalizers));
        }
    }

    @ParallelTest
    public void testLoadBalancerSourceRangeFromListener() {
        List<String> sourceRanges = List.of("10.0.0.0/8", "130.211.204.1/32");

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withListeners(new GenericKafkaListenerBuilder()
                                .withName("external")
                                .withPort(9094)
                                .withType(KafkaListenerType.LOADBALANCER)
                                .withTls(true)
                                .withNewConfiguration()
                                    .withLoadBalancerSourceRanges(sourceRanges)
                                .endConfiguration()
                                .build())
                    .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        // Check external bootstrap service
        Service ext = kc.generateExternalBootstrapServices().get(0);
        assertThat(ext.getSpec().getLoadBalancerSourceRanges(), is(sourceRanges));

        // Check per pod services
        for (int i = 0; i < replicas; i++)  {
            Service srv = kc.generateExternalServices(i).get(0);
            assertThat(srv.getSpec().getLoadBalancerSourceRanges(), is(sourceRanges));
        }
    }

    @ParallelTest
    public void testExternalLoadBalancersWithLabelsAndAnnotations() {
        GenericKafkaListenerConfigurationBootstrap bootstrapConfig = new GenericKafkaListenerConfigurationBootstrapBuilder()
                .withAnnotations(Collections.singletonMap("external-dns.alpha.kubernetes.io/hostname", "bootstrap.myingress.com."))
                .withLabels(Collections.singletonMap("label", "label-value"))
                .build();

        GenericKafkaListenerConfigurationBroker brokerConfig0 = new GenericKafkaListenerConfigurationBrokerBuilder()
                .withBroker(0)
                .withAnnotations(Collections.singletonMap("external-dns.alpha.kubernetes.io/hostname", "broker-0.myingress.com."))
                .withLabels(Collections.singletonMap("label", "label-value"))
                .build();

        GenericKafkaListenerConfigurationBroker brokerConfig2 = new GenericKafkaListenerConfigurationBrokerBuilder()
                .withBroker(2)
                .withAnnotations(Collections.singletonMap("external-dns.alpha.kubernetes.io/hostname", "broker-2.myingress.com."))
                .withLabels(Collections.singletonMap("label", "label-value"))
                .build();

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withListeners(new GenericKafkaListenerBuilder()
                                .withName("external")
                                .withPort(9094)
                                .withType(KafkaListenerType.LOADBALANCER)
                                .withTls(true)
                                .withNewConfiguration()
                                    .withBootstrap(bootstrapConfig)
                                    .withBrokers(brokerConfig0, brokerConfig2)
                                .endConfiguration()
                                .build())
                    .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        // Check annotations
        assertThat(kc.generateExternalBootstrapServices().get(0).getMetadata().getAnnotations(), is(Collections.singletonMap("external-dns.alpha.kubernetes.io/hostname", "bootstrap.myingress.com.")));
        assertThat(kc.generateExternalBootstrapServices().get(0).getMetadata().getLabels().get("label"), is("label-value"));
        assertThat(kc.generateExternalServices(0).get(0).getMetadata().getAnnotations(), is(Collections.singletonMap("external-dns.alpha.kubernetes.io/hostname", "broker-0.myingress.com.")));
        assertThat(kc.generateExternalServices(0).get(0).getMetadata().getLabels().get("label"), is("label-value"));
        assertThat(kc.generateExternalServices(1).get(0).getMetadata().getAnnotations().isEmpty(), is(true));
        assertThat(kc.generateExternalServices(1).get(0).getMetadata().getLabels().get("label"), is(nullValue()));
        assertThat(kc.generateExternalServices(2).get(0).getMetadata().getAnnotations(), is(Collections.singletonMap("external-dns.alpha.kubernetes.io/hostname", "broker-2.myingress.com.")));
        assertThat(kc.generateExternalServices(2).get(0).getMetadata().getLabels().get("label"), is("label-value"));
    }

    @ParallelTest
    public void testExternalLoadBalancersWithLoadBalancerIPOverride() {
        GenericKafkaListenerConfigurationBootstrap bootstrapConfig = new GenericKafkaListenerConfigurationBootstrapBuilder()
                .withLoadBalancerIP("10.0.0.1")
                .build();

        GenericKafkaListenerConfigurationBroker brokerConfig0 = new GenericKafkaListenerConfigurationBrokerBuilder()
                .withBroker(0)
                .withLoadBalancerIP("10.0.0.2")
                .build();

        GenericKafkaListenerConfigurationBroker brokerConfig2 = new GenericKafkaListenerConfigurationBrokerBuilder()
                .withBroker(2)
                .withLoadBalancerIP("10.0.0.3")
                .build();

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withListeners(new GenericKafkaListenerBuilder()
                                .withName("external")
                                .withPort(9094)
                                .withType(KafkaListenerType.LOADBALANCER)
                                .withTls(true)
                                .withNewConfiguration()
                                    .withBootstrap(bootstrapConfig)
                                    .withBrokers(brokerConfig0, brokerConfig2)
                                .endConfiguration()
                                .build())
                    .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        // Check annotations
        assertThat(kc.generateExternalBootstrapServices().get(0).getSpec().getLoadBalancerIP(), is("10.0.0.1"));
        assertThat(kc.generateExternalServices(0).get(0).getSpec().getLoadBalancerIP(), is("10.0.0.2"));
        assertThat(kc.generateExternalServices(1).get(0).getSpec().getLoadBalancerIP(), is(nullValue()));
        assertThat(kc.generateExternalServices(2).get(0).getSpec().getLoadBalancerIP(), is("10.0.0.3"));
    }

    @ParallelTest
    public void testExternalNodePortWithLabelsAndAnnotations() {
        GenericKafkaListenerConfigurationBootstrap bootstrapConfig = new GenericKafkaListenerConfigurationBootstrapBuilder()
                .withAnnotations(Collections.singletonMap("external-dns.alpha.kubernetes.io/hostname", "bootstrap.myingress.com."))
                .withLabels(Collections.singletonMap("label", "label-value"))
                .build();

        GenericKafkaListenerConfigurationBroker brokerConfig0 = new GenericKafkaListenerConfigurationBrokerBuilder()
                .withBroker(0)
                .withAnnotations(Collections.singletonMap("external-dns.alpha.kubernetes.io/hostname", "broker-0.myingress.com."))
                .withLabels(Collections.singletonMap("label", "label-value"))
                .build();

        GenericKafkaListenerConfigurationBroker brokerConfig2 = new GenericKafkaListenerConfigurationBrokerBuilder()
                .withBroker(2)
                .withAnnotations(Collections.singletonMap("external-dns.alpha.kubernetes.io/hostname", "broker-2.myingress.com."))
                .withLabels(Collections.singletonMap("label", "label-value"))
                .build();

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withListeners(new GenericKafkaListenerBuilder()
                                .withName("external")
                                .withPort(9094)
                                .withType(KafkaListenerType.NODEPORT)
                                .withTls(true)
                                .withNewConfiguration()
                                    .withBootstrap(bootstrapConfig)
                                    .withBrokers(brokerConfig0, brokerConfig2)
                                .endConfiguration()
                                .build())
                    .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        // Check annotations
        assertThat(kc.generateExternalBootstrapServices().get(0).getMetadata().getAnnotations(), is(Collections.singletonMap("external-dns.alpha.kubernetes.io/hostname", "bootstrap.myingress.com.")));
        assertThat(kc.generateExternalBootstrapServices().get(0).getMetadata().getLabels().get("label"), is("label-value"));
        assertThat(kc.generateExternalServices(0).get(0).getMetadata().getAnnotations(), is(Collections.singletonMap("external-dns.alpha.kubernetes.io/hostname", "broker-0.myingress.com.")));
        assertThat(kc.generateExternalServices(0).get(0).getMetadata().getLabels().get("label"), is("label-value"));
        assertThat(kc.generateExternalServices(1).get(0).getMetadata().getAnnotations().isEmpty(), is(true));
        assertThat(kc.generateExternalServices(1).get(0).getMetadata().getLabels().get("label"), is(nullValue()));
        assertThat(kc.generateExternalServices(2).get(0).getMetadata().getAnnotations(), is(Collections.singletonMap("external-dns.alpha.kubernetes.io/hostname", "broker-2.myingress.com.")));
        assertThat(kc.generateExternalServices(2).get(0).getMetadata().getLabels().get("label"), is("label-value"));

    }

    @ParallelTest
    public void testNodePortWithLoadbalancer() {
        GenericKafkaListenerConfigurationBootstrap bootstrapConfig = new GenericKafkaListenerConfigurationBootstrapBuilder()
                .withNodePort(32189)
                .build();

        GenericKafkaListenerConfigurationBroker brokerConfig0 = new GenericKafkaListenerConfigurationBrokerBuilder()
                .withBroker(0)
                .withNodePort(32001)
                .build();

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                .editKafka()
                .withListeners(new GenericKafkaListenerBuilder()
                        .withName("external")
                        .withPort(9094)
                        .withType(KafkaListenerType.LOADBALANCER)
                        .withTls(true)
                        .withNewConfiguration()
                        .withBootstrap(bootstrapConfig)
                        .withBrokers(brokerConfig0)
                        .endConfiguration()
                        .build())
                .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        assertThat(kc.generateExternalBootstrapServices().get(0).getSpec().getPorts(), is(Collections.singletonList(kc.createServicePort(ListenersUtils.BACKWARDS_COMPATIBLE_EXTERNAL_PORT_NAME, 9094, 9094, 32189, "TCP"))));
        assertThat(kc.generateExternalServices(0).get(0).getSpec().getPorts().get(0).getNodePort(), is(32001));

        assertThat(ListenersUtils.bootstrapNodePort(kc.getListeners().get(0)), is(32189));
        assertThat(ListenersUtils.brokerNodePort(kc.getListeners().get(0), 0), is(32001));
    }

    @ParallelTest
    public void testExternalNodePorts() {
        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withListeners(new GenericKafkaListenerBuilder()
                                .withName("external")
                                .withPort(9094)
                                .withType(KafkaListenerType.NODEPORT)
                                .withTls(true)
                                .withNewKafkaListenerAuthenticationTlsAuth()
                                .endKafkaListenerAuthenticationTlsAuth()
                                .build())
                    .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        // Check StatefulSet changes
        StatefulSet sts = kc.generateStatefulSet(true, null, null, null);

        List<ContainerPort> ports = sts.getSpec().getTemplate().getSpec().getContainers().get(0).getPorts();
        assertThat(ports.contains(kc.createContainerPort(ListenersUtils.BACKWARDS_COMPATIBLE_EXTERNAL_PORT_NAME, 9094, "TCP")), is(true));

        // Check external bootstrap service
        Service ext = kc.generateExternalBootstrapServices().get(0);
        assertThat(ext.getMetadata().getName(), is(KafkaResources.externalBootstrapServiceName(cluster)));
        assertThat(ext.getSpec().getType(), is("NodePort"));
        assertThat(ext.getSpec().getSelector(), is(kc.getSelectorLabels().toMap()));
        assertThat(ext.getSpec().getPorts(), is(Collections.singletonList(kc.createServicePort(ListenersUtils.BACKWARDS_COMPATIBLE_EXTERNAL_PORT_NAME, 9094, 9094, "TCP"))));
        checkOwnerReference(kc.createOwnerReference(), ext);

        // Check per pod services
        for (int i = 0; i < replicas; i++)  {
            Service srv = kc.generateExternalServices(i).get(0);
            assertThat(srv.getMetadata().getName(), is(KafkaResources.kafkaStatefulSetName(cluster) + "-" + i));
            assertThat(srv.getSpec().getType(), is("NodePort"));
            assertThat(srv.getSpec().getSelector().get(Labels.KUBERNETES_STATEFULSET_POD_LABEL), is(KafkaResources.kafkaPodName(cluster, i)));
            assertThat(srv.getSpec().getPorts(), is(Collections.singletonList(kc.createServicePort(ListenersUtils.BACKWARDS_COMPATIBLE_EXTERNAL_PORT_NAME, 9094, 9094, "TCP"))));
            checkOwnerReference(kc.createOwnerReference(), srv);
        }
    }

    @ParallelTest
    public void testExternalNodePortsWithAddressType() {
        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withListeners(new GenericKafkaListenerBuilder()
                                .withName("external")
                                .withPort(9094)
                                .withType(KafkaListenerType.NODEPORT)
                                .withTls(true)
                                .withNewConfiguration()
                                    .withPreferredNodePortAddressType(NodeAddressType.INTERNAL_DNS)
                                .endConfiguration()
                                .build())
                    .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        // Check StatefulSet changes
        StatefulSet sts = kc.generateStatefulSet(true, null, null, null);
        Container initCont = sts.getSpec().getTemplate().getSpec().getInitContainers().get(0);

        assertThat(initCont, is(notNullValue()));
        assertThat(initCont.getEnv().stream().filter(env -> KafkaCluster.ENV_VAR_KAFKA_INIT_EXTERNAL_ADDRESS.equals(env.getName()))
                        .map(EnvVar::getValue).findFirst().orElse(""), is("TRUE"));
    }

    @ParallelTest
    public void testExternalNodePortOverrides() {
        GenericKafkaListenerConfigurationBroker nodePortListenerBrokerConfig = new GenericKafkaListenerConfigurationBroker();
        nodePortListenerBrokerConfig.setBroker(0);
        nodePortListenerBrokerConfig.setNodePort(32101);

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
            image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
            .editSpec()
                .editKafka()
                    .withListeners(new GenericKafkaListenerBuilder()
                            .withName("external")
                            .withPort(9094)
                            .withType(KafkaListenerType.NODEPORT)
                            .withTls(false)
                            .withNewConfiguration()
                                .withNewBootstrap()
                                    .withNodePort(32001)
                                .endBootstrap()
                                .withBrokers(nodePortListenerBrokerConfig)
                            .endConfiguration()
                            .build())
                .endKafka()
            .endSpec()
            .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        // Check StatefulSet changes
        StatefulSet sts = kc.generateStatefulSet(true, null, null, null);

        List<ContainerPort> ports = sts.getSpec().getTemplate().getSpec().getContainers().get(0).getPorts();
        assertThat(ports.contains(kc.createContainerPort(ListenersUtils.BACKWARDS_COMPATIBLE_EXTERNAL_PORT_NAME, 9094, "TCP")), is(true));

        // Check external bootstrap service
        Service ext = kc.generateExternalBootstrapServices().get(0);
        assertThat(ext.getMetadata().getName(), is(KafkaResources.externalBootstrapServiceName(cluster)));
        assertThat(ext.getSpec().getType(), is("NodePort"));
        assertThat(ext.getSpec().getSelector(), is(kc.getSelectorLabels().toMap()));
        assertThat(ext.getSpec().getPorts(), is(Collections.singletonList(kc.createServicePort(ListenersUtils.BACKWARDS_COMPATIBLE_EXTERNAL_PORT_NAME, 9094, 9094, 32001, "TCP"))));
        checkOwnerReference(kc.createOwnerReference(), ext);

        // Check per pod services
        for (int i = 0; i < replicas; i++)  {
            Service srv = kc.generateExternalServices(i).get(0);
            assertThat(srv.getMetadata().getName(), is(KafkaResources.kafkaStatefulSetName(cluster) + "-" + i));
            assertThat(srv.getSpec().getType(), is("NodePort"));
            assertThat(srv.getSpec().getSelector().get(Labels.KUBERNETES_STATEFULSET_POD_LABEL), is(KafkaResources.kafkaPodName(cluster, i)));
            if (i == 0) { // pod with index 0 will have overriden port
                assertThat(srv.getSpec().getPorts(), is(Collections.singletonList(kc.createServicePort(ListenersUtils.BACKWARDS_COMPATIBLE_EXTERNAL_PORT_NAME, 9094, 9094, 32101, "TCP"))));
            } else {
                assertThat(srv.getSpec().getPorts(), is(Collections.singletonList(kc.createServicePort(ListenersUtils.BACKWARDS_COMPATIBLE_EXTERNAL_PORT_NAME, 9094, 9094, "TCP"))));
            }
            checkOwnerReference(kc.createOwnerReference(), srv);
        }
    }

    @ParallelTest
    public void testGetExternalNodePortServiceAddressOverrideWithNullAdvertisedHost() {
        GenericKafkaListenerConfigurationBroker nodePortListenerBrokerConfig = new GenericKafkaListenerConfigurationBroker();
        nodePortListenerBrokerConfig.setBroker(0);
        nodePortListenerBrokerConfig.setNodePort(32101);

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
            image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
            .editSpec()
                .editKafka()
                    .withListeners(new GenericKafkaListenerBuilder()
                            .withName("external")
                            .withPort(9094)
                            .withType(KafkaListenerType.NODEPORT)
                            .withTls(false)
                            .withNewConfiguration()
                                .withNewBootstrap()
                                    .withNodePort(32001)
                                .endBootstrap()
                                .withBrokers(nodePortListenerBrokerConfig)
                            .endConfiguration()
                            .build())
                .endKafka()
            .endSpec()
            .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        assertThat(kc.generateExternalServices(0).get(0).getSpec().getPorts().get(0).getNodePort(), is(32101));
        assertThat(kc.generateExternalBootstrapServices().get(0).getSpec().getPorts().get(0).getNodePort(), is(32001));
        assertThat(ListenersUtils.bootstrapNodePort(kc.getListeners().get(0)), is(32001));
        assertThat(ListenersUtils.brokerNodePort(kc.getListeners().get(0), 0), is(32101));
    }

    @ParallelTest
    public void testGetExternalNodePortServiceAddressOverrideWithNonNullAdvertisedHost() {
        GenericKafkaListenerConfigurationBroker nodePortListenerBrokerConfig = new GenericKafkaListenerConfigurationBroker();
        nodePortListenerBrokerConfig.setBroker(0);
        nodePortListenerBrokerConfig.setNodePort(32101);
        nodePortListenerBrokerConfig.setAdvertisedHost("advertised.host");

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
            image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
            .editSpec()
                .editKafka()
                    .withListeners(new GenericKafkaListenerBuilder()
                            .withName("external")
                            .withPort(9094)
                            .withType(KafkaListenerType.NODEPORT)
                            .withTls(false)
                            .withNewConfiguration()
                                .withNewBootstrap()
                                    .withNodePort(32001)
                                .endBootstrap()
                                .withBrokers(nodePortListenerBrokerConfig)
                            .endConfiguration()
                            .build())
                .endKafka()
            .endSpec()
            .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        assertThat(kc.generateExternalServices(0).get(0).getSpec().getPorts().get(0).getNodePort(), is(32101));
        assertThat(kc.generateExternalBootstrapServices().get(0).getSpec().getPorts().get(0).getNodePort(), is(32001));

        assertThat(ListenersUtils.bootstrapNodePort(kc.getListeners().get(0)), is(32001));
        assertThat(ListenersUtils.brokerNodePort(kc.getListeners().get(0), 0), is(32101));
        assertThat(ListenersUtils.brokerAdvertisedHost(kc.getListeners().get(0), 0), is("advertised.host"));
    }

    @ParallelTest
    public void testGenerateBrokerSecret() throws CertificateParsingException {
        Secret secret = generateBrokerSecret(null, emptyMap());
        assertThat(secret.getData().keySet(), is(set(
                "foo-kafka-0.crt",  "foo-kafka-0.key", "foo-kafka-0.p12", "foo-kafka-0.password",
                "foo-kafka-1.crt", "foo-kafka-1.key", "foo-kafka-1.p12", "foo-kafka-1.password",
                "foo-kafka-2.crt", "foo-kafka-2.key", "foo-kafka-2.p12", "foo-kafka-2.password")));
        X509Certificate cert = Ca.cert(secret, "foo-kafka-0.crt");
        assertThat(cert.getSubjectDN().getName(), is("CN=foo-kafka, O=io.strimzi"));
        assertThat(new HashSet<Object>(cert.getSubjectAlternativeNames()), is(set(
                asList(2, "foo-kafka-0.foo-kafka-brokers.test.svc.cluster.local"),
                asList(2, "foo-kafka-0.foo-kafka-brokers.test.svc"),
                asList(2, "foo-kafka-bootstrap"),
                asList(2, "foo-kafka-bootstrap.test"),
                asList(2, "foo-kafka-bootstrap.test.svc"),
                asList(2, "foo-kafka-bootstrap.test.svc.cluster.local"),
                asList(2, "foo-kafka-brokers"),
                asList(2, "foo-kafka-brokers.test"),
                asList(2, "foo-kafka-brokers.test.svc"),
                asList(2, "foo-kafka-brokers.test.svc.cluster.local"))));

    }

    @ParallelTest
    public void testGenerateBrokerSecretExternal() throws CertificateParsingException {
        Map<Integer, Set<String>> externalAddresses = new HashMap<>();
        externalAddresses.put(0, Collections.singleton("123.10.125.130"));
        externalAddresses.put(1, Collections.singleton("123.10.125.131"));
        externalAddresses.put(2, Collections.singleton("123.10.125.132"));

        Secret secret = generateBrokerSecret(Collections.singleton("123.10.125.140"), externalAddresses);
        assertThat(secret.getData().keySet(), is(set(
                "foo-kafka-0.crt",  "foo-kafka-0.key", "foo-kafka-0.p12", "foo-kafka-0.password",
                "foo-kafka-1.crt", "foo-kafka-1.key", "foo-kafka-1.p12", "foo-kafka-1.password",
                "foo-kafka-2.crt", "foo-kafka-2.key", "foo-kafka-2.p12", "foo-kafka-2.password")));
        X509Certificate cert = Ca.cert(secret, "foo-kafka-0.crt");
        assertThat(cert.getSubjectDN().getName(), is("CN=foo-kafka, O=io.strimzi"));
        assertThat(new HashSet<Object>(cert.getSubjectAlternativeNames()), is(set(
                asList(2, "foo-kafka-0.foo-kafka-brokers.test.svc.cluster.local"),
                asList(2, "foo-kafka-0.foo-kafka-brokers.test.svc"),
                asList(2, "foo-kafka-bootstrap"),
                asList(2, "foo-kafka-bootstrap.test"),
                asList(2, "foo-kafka-bootstrap.test.svc"),
                asList(2, "foo-kafka-bootstrap.test.svc.cluster.local"),
                asList(2, "foo-kafka-brokers"),
                asList(2, "foo-kafka-brokers.test"),
                asList(2, "foo-kafka-brokers.test.svc"),
                asList(2, "foo-kafka-brokers.test.svc.cluster.local"),
                asList(7, "123.10.125.140"),
                asList(7, "123.10.125.130"))));
    }

    @ParallelTest
    public void testGenerateBrokerSecretExternalWithManyDNS() throws CertificateParsingException {
        Map<Integer, Set<String>> externalAddresses = new HashMap<>();
        externalAddresses.put(0, TestUtils.set("123.10.125.130", "my-broker-0"));
        externalAddresses.put(1, TestUtils.set("123.10.125.131", "my-broker-1"));
        externalAddresses.put(2, TestUtils.set("123.10.125.132", "my-broker-2"));

        Secret secret = generateBrokerSecret(TestUtils.set("123.10.125.140", "my-bootstrap"), externalAddresses);
        assertThat(secret.getData().keySet(), is(set(
                "foo-kafka-0.crt",  "foo-kafka-0.key", "foo-kafka-0.p12", "foo-kafka-0.password",
                "foo-kafka-1.crt", "foo-kafka-1.key", "foo-kafka-1.p12", "foo-kafka-1.password",
                "foo-kafka-2.crt", "foo-kafka-2.key", "foo-kafka-2.p12", "foo-kafka-2.password")));
        X509Certificate cert = Ca.cert(secret, "foo-kafka-0.crt");
        assertThat(cert.getSubjectDN().getName(), is("CN=foo-kafka, O=io.strimzi"));
        assertThat(new HashSet<Object>(cert.getSubjectAlternativeNames()), is(set(
                asList(2, "foo-kafka-0.foo-kafka-brokers.test.svc.cluster.local"),
                asList(2, "foo-kafka-0.foo-kafka-brokers.test.svc"),
                asList(2, "foo-kafka-bootstrap"),
                asList(2, "foo-kafka-bootstrap.test"),
                asList(2, "foo-kafka-bootstrap.test.svc"),
                asList(2, "foo-kafka-bootstrap.test.svc.cluster.local"),
                asList(2, "foo-kafka-brokers"),
                asList(2, "foo-kafka-brokers.test"),
                asList(2, "foo-kafka-brokers.test.svc"),
                asList(2, "foo-kafka-brokers.test.svc.cluster.local"),
                asList(2, "my-broker-0"),
                asList(2, "my-bootstrap"),
                asList(7, "123.10.125.140"),
                asList(7, "123.10.125.130"))));
    }

    private Secret generateBrokerSecret(Set<String> externalBootstrapAddress, Map<Integer, Set<String>> externalAddresses) {
        ClusterCa clusterCa = new ClusterCa(Reconciliation.DUMMY_RECONCILIATION, new OpenSslCertManager(), new PasswordGenerator(10, "a", "a"), cluster, null, null);
        clusterCa.createRenewOrReplace(namespace, cluster, emptyMap(), emptyMap(), emptyMap(), null, true);
        ClientsCa clientsCa = new ClientsCa(Reconciliation.DUMMY_RECONCILIATION, new OpenSslCertManager(), new PasswordGenerator(10, "a", "a"), null, null, null, null, 365, 30, true, CertificateExpirationPolicy.RENEW_CERTIFICATE);
        clientsCa.createRenewOrReplace(namespace, cluster, emptyMap(), emptyMap(), emptyMap(), null, true);

        return kc.generateCertificatesSecret(clusterCa, clientsCa, externalBootstrapAddress, externalAddresses, true);
    }

    @ParallelTest
    @SuppressWarnings({"checkstyle:MethodLength"})
    public void testTemplate() {
        Map<String, String> ssLabels = TestUtils.map("l1", "v1", "l2", "v2",
                Labels.KUBERNETES_PART_OF_LABEL, "custom-part",
                Labels.KUBERNETES_MANAGED_BY_LABEL, "custom-managed-by");
        Map<String, String> expectedStsLabels = new HashMap<>(ssLabels);
        expectedStsLabels.remove(Labels.KUBERNETES_MANAGED_BY_LABEL);
        Map<String, String> ssAnnotations = TestUtils.map("a1", "v1", "a2", "v2");

        Map<String, String> podLabels = TestUtils.map("l3", "v3", "l4", "v4");
        Map<String, String> podAnnotations = TestUtils.map("a3", "v3", "a4", "v4");

        Map<String, String> svcLabels = TestUtils.map("l5", "v5", "l6", "v6");
        Map<String, String> svcAnnotations = TestUtils.map("a5", "v5", "a6", "v6");

        Map<String, String> hSvcLabels = TestUtils.map("l7", "v7", "l8", "v8");
        Map<String, String> hSvcAnnotations = TestUtils.map("a7", "v7", "a8", "v8");

        Map<String, String> exSvcLabels = TestUtils.map("l9", "v9", "l10", "v10");
        Map<String, String> exSvcAnnotations = TestUtils.map("a9", "v9", "a10", "v10");

        Map<String, String> perPodSvcLabels = TestUtils.map("l11", "v11", "l12", "v12");
        Map<String, String> perPodSvcAnnotations = TestUtils.map("a11", "v11", "a12", "v12");

        Map<String, String> exRouteLabels = TestUtils.map("l13", "v13", "l14", "v14");
        Map<String, String> exRouteAnnotations = TestUtils.map("a13", "v13", "a14", "v14");

        Map<String, String> perPodRouteLabels = TestUtils.map("l15", "v15", "l16", "v16");
        Map<String, String> perPodRouteAnnotations = TestUtils.map("a15", "v15", "a16", "v16");

        Map<String, String> pdbLabels = TestUtils.map("l17", "v17", "l18", "v18");
        Map<String, String> pdbAnnotations = TestUtils.map("a17", "v17", "a18", "v18");

        Map<String, String> crbLabels = TestUtils.map("l19", "v19", "l20", "v20");
        Map<String, String> crbAnnotations = TestUtils.map("a19", "v19", "a20", "v20");

        Map<String, String> saLabels = TestUtils.map("l21", "v21", "l22", "v22");
        Map<String, String> saAnnotations = TestUtils.map("a21", "v21", "a22", "v22");

        HostAlias hostAlias1 = new HostAliasBuilder()
                        .withHostnames("my-host-1", "my-host-2")
                        .withIp("192.168.1.86")
                        .build();
        HostAlias hostAlias2 = new HostAliasBuilder()
                        .withHostnames("my-host-3")
                        .withIp("192.168.1.87")
                        .build();

        TopologySpreadConstraint tsc1 = new TopologySpreadConstraintBuilder()
                .withTopologyKey("kubernetes.io/zone")
                .withMaxSkew(1)
                .withWhenUnsatisfiable("DoNotSchedule")
                .withLabelSelector(new LabelSelectorBuilder().withMatchLabels(singletonMap("label", "value")).build())
                .build();

        TopologySpreadConstraint tsc2 = new TopologySpreadConstraintBuilder()
                .withTopologyKey("kubernetes.io/hostname")
                .withMaxSkew(2)
                .withWhenUnsatisfiable("ScheduleAnyway")
                .withLabelSelector(new LabelSelectorBuilder().withMatchLabels(singletonMap("label", "value")).build())
                .build();

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withListeners(new GenericKafkaListenerBuilder()
                                    .withName("external")
                                    .withPort(9094)
                                    .withType(KafkaListenerType.ROUTE)
                                    .withTls(true)
                                    .build(),
                                new GenericKafkaListenerBuilder()
                                    .withName("external2")
                                    .withPort(9095)
                                    .withType(KafkaListenerType.NODEPORT)
                                    .withTls(true)
                                    .build())
                        .withNewTemplate()
                            .withNewStatefulset()
                                .withNewMetadata()
                                    .withLabels(ssLabels)
                                    .withAnnotations(ssAnnotations)
                                .endMetadata()
                            .endStatefulset()
                            .withNewPod()
                                .withNewMetadata()
                                    .withLabels(podLabels)
                                    .withAnnotations(podAnnotations)
                                .endMetadata()
                                .withPriorityClassName("top-priority")
                                .withSchedulerName("my-scheduler")
                                .withHostAliases(hostAlias1, hostAlias2)
                                .withTopologySpreadConstraints(tsc1, tsc2)
                                .withEnableServiceLinks(false)
                                .withTmpDirSizeLimit("10Mi")
                            .endPod()
                            .withNewBootstrapService()
                                .withNewMetadata()
                                    .withLabels(svcLabels)
                                    .withAnnotations(svcAnnotations)
                                .endMetadata()
                                .withIpFamilyPolicy(IpFamilyPolicy.PREFER_DUAL_STACK)
                                .withIpFamilies(IpFamily.IPV6, IpFamily.IPV4)
                            .endBootstrapService()
                            .withNewBrokersService()
                                .withNewMetadata()
                                    .withLabels(hSvcLabels)
                                    .withAnnotations(hSvcAnnotations)
                                .endMetadata()
                                .withIpFamilyPolicy(IpFamilyPolicy.SINGLE_STACK)
                                .withIpFamilies(IpFamily.IPV6)
                            .endBrokersService()
                            .withNewExternalBootstrapService()
                                .withNewMetadata()
                                    .withLabels(exSvcLabels)
                                    .withAnnotations(exSvcAnnotations)
                                .endMetadata()
                            .endExternalBootstrapService()
                            .withNewPerPodService()
                                .withNewMetadata()
                                    .withLabels(perPodSvcLabels)
                                    .withAnnotations(perPodSvcAnnotations)
                                .endMetadata()
                            .endPerPodService()
                            .withNewExternalBootstrapRoute()
                                .withNewMetadata()
                                .withLabels(exRouteLabels)
                                .withAnnotations(exRouteAnnotations)
                                .endMetadata()
                            .endExternalBootstrapRoute()
                            .withNewPerPodRoute()
                                .withNewMetadata()
                                .withLabels(perPodRouteLabels)
                                .withAnnotations(perPodRouteAnnotations)
                                .endMetadata()
                            .endPerPodRoute()
                            .withNewPodDisruptionBudget()
                                .withNewMetadata()
                                    .withLabels(pdbLabels)
                                    .withAnnotations(pdbAnnotations)
                                .endMetadata()
                            .endPodDisruptionBudget()
                            .withNewClusterRoleBinding()
                                .withNewMetadata()
                                    .withLabels(crbLabels)
                                    .withAnnotations(crbAnnotations)
                                .endMetadata()
                            .endClusterRoleBinding()
                            .withNewServiceAccount()
                                .withNewMetadata()
                                    .withLabels(saLabels)
                                    .withAnnotations(saAnnotations)
                                .endMetadata()
                            .endServiceAccount()
                        .endTemplate()
                    .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        // Check StatefulSet
        StatefulSet sts = kc.generateStatefulSet(true, null, null, null);
        assertThat(sts.getMetadata().getLabels().entrySet().containsAll(expectedStsLabels.entrySet()), is(true));
        assertThat(sts.getMetadata().getAnnotations().entrySet().containsAll(ssAnnotations.entrySet()), is(true));
        assertThat(sts.getSpec().getTemplate().getSpec().getPriorityClassName(), is("top-priority"));

        // Check Pods
        assertThat(sts.getSpec().getTemplate().getMetadata().getLabels().entrySet().containsAll(podLabels.entrySet()), is(true));
        assertThat(sts.getSpec().getTemplate().getMetadata().getAnnotations().entrySet().containsAll(podAnnotations.entrySet()), is(true));
        assertThat(sts.getSpec().getTemplate().getSpec().getSchedulerName(), is("my-scheduler"));
        assertThat(sts.getSpec().getTemplate().getSpec().getHostAliases(), containsInAnyOrder(hostAlias1, hostAlias2));
        assertThat(sts.getSpec().getTemplate().getSpec().getTopologySpreadConstraints(), containsInAnyOrder(tsc1, tsc2));
        assertThat(sts.getSpec().getTemplate().getSpec().getEnableServiceLinks(), is(false));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream()
            .filter(volume -> volume.getName().equalsIgnoreCase("strimzi-tmp"))
            .findFirst().orElseThrow().getEmptyDir().getSizeLimit(), is(new Quantity("10Mi")));

        // Check Service
        Service svc = kc.generateService();
        assertThat(svc.getMetadata().getLabels().entrySet().containsAll(svcLabels.entrySet()), is(true));
        assertThat(svc.getMetadata().getAnnotations().entrySet().containsAll(svcAnnotations.entrySet()), is(true));
        assertThat(svc.getSpec().getIpFamilyPolicy(), is("PreferDualStack"));
        assertThat(svc.getSpec().getIpFamilies(), contains("IPv6", "IPv4"));

        // Check Headless Service
        svc = kc.generateHeadlessService();
        assertThat(svc.getMetadata().getLabels().entrySet().containsAll(hSvcLabels.entrySet()), is(true));
        assertThat(svc.getMetadata().getAnnotations().entrySet().containsAll(hSvcAnnotations.entrySet()), is(true));
        assertThat(svc.getSpec().getIpFamilyPolicy(), is("SingleStack"));
        assertThat(svc.getSpec().getIpFamilies(), contains("IPv6"));

        // Check External Bootstrap service
        svc = kc.generateExternalBootstrapServices().get(0);
        assertThat(svc.getMetadata().getLabels().entrySet().containsAll(exSvcLabels.entrySet()), is(true));
        assertThat(svc.getMetadata().getAnnotations().entrySet().containsAll(exSvcAnnotations.entrySet()), is(true));

        // Check per pod service
        svc = kc.generateExternalServices(0).get(0);
        assertThat(svc.getMetadata().getLabels().entrySet().containsAll(perPodSvcLabels.entrySet()), is(true));
        assertThat(svc.getMetadata().getAnnotations().entrySet().containsAll(perPodSvcAnnotations.entrySet()), is(true));

        // Check Bootstrap Route
        Route rt = kc.generateExternalBootstrapRoutes().get(0);
        assertThat(rt.getMetadata().getLabels().entrySet().containsAll(exRouteLabels.entrySet()), is(true));
        assertThat(rt.getMetadata().getAnnotations().entrySet().containsAll(exRouteAnnotations.entrySet()), is(true));

        // Check PerPodRoute
        rt = kc.generateExternalRoutes(0).get(0);
        assertThat(rt.getMetadata().getLabels().entrySet().containsAll(perPodRouteLabels.entrySet()), is(true));
        assertThat(rt.getMetadata().getAnnotations().entrySet().containsAll(perPodRouteAnnotations.entrySet()), is(true));

        // Check PodDisruptionBudget
        PodDisruptionBudget pdb = kc.generatePodDisruptionBudget();
        assertThat(pdb.getMetadata().getLabels().entrySet().containsAll(pdbLabels.entrySet()), is(true));
        assertThat(pdb.getMetadata().getAnnotations().entrySet().containsAll(pdbAnnotations.entrySet()), is(true));

        // Check PodDisruptionBudget V1Beta1
        io.fabric8.kubernetes.api.model.policy.v1beta1.PodDisruptionBudget pdbV1Beta1 = kc.generatePodDisruptionBudgetV1Beta1();
        assertThat(pdbV1Beta1.getMetadata().getLabels().entrySet().containsAll(pdbLabels.entrySet()), is(true));
        assertThat(pdbV1Beta1.getMetadata().getAnnotations().entrySet().containsAll(pdbAnnotations.entrySet()), is(true));

        // Check ClusterRoleBinding
        ClusterRoleBinding crb = kc.generateClusterRoleBinding("namespace");
        assertThat(crb.getMetadata().getLabels().entrySet().containsAll(crbLabels.entrySet()), is(true));
        assertThat(crb.getMetadata().getAnnotations().entrySet().containsAll(crbAnnotations.entrySet()), is(true));

        // Check Service Account
        ServiceAccount sa = kc.generateServiceAccount();
        assertThat(sa.getMetadata().getLabels().entrySet().containsAll(saLabels.entrySet()), is(true));
        assertThat(sa.getMetadata().getAnnotations().entrySet().containsAll(saAnnotations.entrySet()), is(true));
    }

    @ParallelTest
    public void testControlPlanePortNetworkPolicy() {
        NetworkPolicyPeer kafkaBrokersPeer = new NetworkPolicyPeerBuilder()
                .withNewPodSelector()
                    .withMatchLabels(Collections.singletonMap(Labels.STRIMZI_NAME_LABEL, KafkaResources.kafkaStatefulSetName(cluster)))
                .endPodSelector()
                .build();

        Kafka kafkaAssembly = ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap());
        KafkaCluster k = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        // Check Network Policies => Different namespace
        NetworkPolicy np = k.generateNetworkPolicy("operator-namespace", null);

        assertThat(np.getSpec().getIngress().stream().filter(ing -> ing.getPorts().get(0).getPort().equals(new IntOrString(KafkaCluster.CONTROLPLANE_PORT))).findFirst().orElse(null), is(notNullValue()));

        List<NetworkPolicyPeer> rules = np.getSpec().getIngress().stream().filter(ing -> ing.getPorts().get(0).getPort().equals(new IntOrString(KafkaCluster.CONTROLPLANE_PORT))).map(NetworkPolicyIngressRule::getFrom).findFirst().orElseThrow();

        assertThat(rules.size(), is(1));
        assertThat(rules.contains(kafkaBrokersPeer), is(true));
    }

    @ParallelTest
    public void testReplicationPortNetworkPolicy() {
        NetworkPolicyPeer kafkaBrokersPeer = new NetworkPolicyPeerBuilder()
                .withNewPodSelector()
                    .withMatchLabels(Collections.singletonMap(Labels.STRIMZI_NAME_LABEL, KafkaResources.kafkaStatefulSetName(cluster)))
                .endPodSelector()
                .build();

        NetworkPolicyPeer eoPeer = new NetworkPolicyPeerBuilder()
                .withNewPodSelector()
                    .withMatchLabels(Collections.singletonMap(Labels.STRIMZI_NAME_LABEL, KafkaResources.entityOperatorDeploymentName(cluster)))
                .endPodSelector()
                .build();

        NetworkPolicyPeer kafkaExporterPeer = new NetworkPolicyPeerBuilder()
                .withNewPodSelector()
                    .withMatchLabels(Collections.singletonMap(Labels.STRIMZI_NAME_LABEL, KafkaExporterResources.deploymentName(cluster)))
                .endPodSelector()
                .build();

        NetworkPolicyPeer cruiseControlPeer = new NetworkPolicyPeerBuilder()
                .withNewPodSelector()
                    .withMatchLabels(Collections.singletonMap(Labels.STRIMZI_NAME_LABEL, CruiseControlResources.deploymentName(cluster)))
                .endPodSelector()
                .build();

        NetworkPolicyPeer clusterOperatorPeer = new NetworkPolicyPeerBuilder()
                .withNewPodSelector()
                    .withMatchLabels(Collections.singletonMap(Labels.STRIMZI_KIND_LABEL, "cluster-operator"))
                .endPodSelector()
                .withNewNamespaceSelector().endNamespaceSelector()
                .build();

        NetworkPolicyPeer clusterOperatorPeerSameNamespace = new NetworkPolicyPeerBuilder()
                .withNewPodSelector()
                    .withMatchLabels(Collections.singletonMap(Labels.STRIMZI_KIND_LABEL, "cluster-operator"))
                .endPodSelector()
                .build();

        NetworkPolicyPeer clusterOperatorPeerNamespaceWithLabels = new NetworkPolicyPeerBuilder()
                .withNewPodSelector()
                .withMatchLabels(Collections.singletonMap(Labels.STRIMZI_KIND_LABEL, "cluster-operator"))
                .endPodSelector()
                .withNewNamespaceSelector()
                    .withMatchLabels(Collections.singletonMap("nsLabelKey", "nsLabelValue"))
                .endNamespaceSelector()
                .build();

        Kafka kafkaAssembly = ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap());
        KafkaCluster k = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        // Check Network Policies => Different namespace
        NetworkPolicy np = k.generateNetworkPolicy("operator-namespace", null);

        assertThat(np.getSpec().getIngress().stream().filter(ing -> ing.getPorts().get(0).getPort().equals(new IntOrString(KafkaCluster.REPLICATION_PORT))).findFirst().orElse(null), is(notNullValue()));

        List<NetworkPolicyPeer> rules = np.getSpec().getIngress().stream().filter(ing -> ing.getPorts().get(0).getPort().equals(new IntOrString(KafkaCluster.REPLICATION_PORT))).map(NetworkPolicyIngressRule::getFrom).findFirst().orElseThrow();

        assertThat(rules.size(), is(5));
        assertThat(rules.contains(kafkaBrokersPeer), is(true));
        assertThat(rules.contains(eoPeer), is(true));
        assertThat(rules.contains(kafkaExporterPeer), is(true));
        assertThat(rules.contains(cruiseControlPeer), is(true));
        assertThat(rules.contains(clusterOperatorPeer), is(true));

        // Check Network Policies => Same namespace
        np = k.generateNetworkPolicy(namespace, null);

        assertThat(np.getSpec().getIngress().stream().filter(ing -> ing.getPorts().get(0).getPort().equals(new IntOrString(KafkaCluster.REPLICATION_PORT))).findFirst().orElse(null), is(notNullValue()));

        rules = np.getSpec().getIngress().stream().filter(ing -> ing.getPorts().get(0).getPort().equals(new IntOrString(KafkaCluster.REPLICATION_PORT))).map(NetworkPolicyIngressRule::getFrom).findFirst().orElseThrow();

        assertThat(rules.size(), is(5));
        assertThat(rules.contains(kafkaBrokersPeer), is(true));
        assertThat(rules.contains(eoPeer), is(true));
        assertThat(rules.contains(kafkaExporterPeer), is(true));
        assertThat(rules.contains(cruiseControlPeer), is(true));
        assertThat(rules.contains(clusterOperatorPeerSameNamespace), is(true));

        // Check Network Policies => Namespace with Labels
        np = k.generateNetworkPolicy("operator-namespace", Labels.fromMap(Collections.singletonMap("nsLabelKey", "nsLabelValue")));

        assertThat(np.getSpec().getIngress().stream().filter(ing -> ing.getPorts().get(0).getPort().equals(new IntOrString(KafkaCluster.REPLICATION_PORT))).findFirst().orElse(null), is(notNullValue()));

        rules = np.getSpec().getIngress().stream().filter(ing -> ing.getPorts().get(0).getPort().equals(new IntOrString(KafkaCluster.REPLICATION_PORT))).map(NetworkPolicyIngressRule::getFrom).findFirst().orElseThrow();

        assertThat(rules.size(), is(5));
        assertThat(rules.contains(kafkaBrokersPeer), is(true));
        assertThat(rules.contains(eoPeer), is(true));
        assertThat(rules.contains(kafkaExporterPeer), is(true));
        assertThat(rules.contains(cruiseControlPeer), is(true));
        assertThat(rules.contains(clusterOperatorPeerNamespaceWithLabels), is(true));
    }

    @ParallelTest
    public void testNetworkPolicyPeers() {
        NetworkPolicyPeer peer1 = new NetworkPolicyPeerBuilder()
                .withNewPodSelector()
                    .withMatchExpressions(new LabelSelectorRequirementBuilder().withKey("my-key1").withValues("my-value1").build())
                .endPodSelector()
                .build();

        NetworkPolicyPeer peer2 = new NetworkPolicyPeerBuilder()
                .withNewNamespaceSelector()
                    .withMatchExpressions(new LabelSelectorRequirementBuilder().withKey("my-key2").withValues("my-value2").build())
                .endNamespaceSelector()
                .build();

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withListeners(new GenericKafkaListenerBuilder()
                                    .withName("plain")
                                    .withPort(9092)
                                    .withType(KafkaListenerType.INTERNAL)
                                    .withNetworkPolicyPeers(peer1)
                                    .withTls(false)
                                    .build(),
                                new GenericKafkaListenerBuilder()
                                    .withName("tls")
                                    .withPort(9093)
                                    .withType(KafkaListenerType.INTERNAL)
                                    .withTls(true)
                                    .withNetworkPolicyPeers(peer2)
                                    .build(),
                                new GenericKafkaListenerBuilder()
                                    .withName("external")
                                    .withPort(9094)
                                    .withType(KafkaListenerType.ROUTE)
                                    .withTls(true)
                                    .withNetworkPolicyPeers(peer1, peer2)
                                    .build())
                    .endKafka()
                .endSpec()
                .build();
        KafkaCluster k = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        // Check Network Policies
        NetworkPolicy np = k.generateNetworkPolicy(null, null);

        List<NetworkPolicyIngressRule> rules = np.getSpec().getIngress().stream().filter(ing -> ing.getPorts().get(0).getPort().equals(new IntOrString(9092))).collect(Collectors.toList());
        assertThat(rules.size(), is(1));
        assertThat(rules.get(0).getFrom().get(0), is(peer1));

        rules = np.getSpec().getIngress().stream().filter(ing -> ing.getPorts().get(0).getPort().equals(new IntOrString(9093))).collect(Collectors.toList());
        assertThat(rules.size(), is(1));
        assertThat(rules.get(0).getFrom().get(0), is(peer2));

        rules = np.getSpec().getIngress().stream().filter(ing -> ing.getPorts().get(0).getPort().equals(new IntOrString(9094))).collect(Collectors.toList());
        assertThat(rules.size(), is(1));
        assertThat(rules.get(0).getFrom().size(), is(2));
        assertThat(rules.get(0).getFrom().contains(peer1), is(true));
        assertThat(rules.get(0).getFrom().contains(peer2), is(true));
    }

    @ParallelTest
    public void testNoNetworkPolicyPeers() {
        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withListeners(new GenericKafkaListenerBuilder()
                                    .withName("plain")
                                    .withPort(9092)
                                    .withType(KafkaListenerType.INTERNAL)
                                    .withTls(false)
                                    .build(),
                                new GenericKafkaListenerBuilder()
                                    .withName("tls")
                                    .withPort(9093)
                                    .withType(KafkaListenerType.INTERNAL)
                                    .withTls(true)
                                    .build(),
                                new GenericKafkaListenerBuilder()
                                    .withName("external")
                                    .withPort(9094)
                                    .withType(KafkaListenerType.ROUTE)
                                    .withTls(true)
                                    .build())
                    .endKafka()
                .endSpec()
                .build();
        KafkaCluster k = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        // Check Network Policies
        NetworkPolicy np = k.generateNetworkPolicy(null, null);

        List<NetworkPolicyIngressRule> rules = np.getSpec().getIngress().stream().filter(ing -> ing.getPorts().get(0).getPort().equals(new IntOrString(9092))).collect(Collectors.toList());
        assertThat(rules.size(), is(1));
        assertThat(rules.get(0).getFrom(), is(nullValue()));

        rules = np.getSpec().getIngress().stream().filter(ing -> ing.getPorts().get(0).getPort().equals(new IntOrString(9093))).collect(Collectors.toList());
        assertThat(rules.size(), is(1));
        assertThat(rules.get(0).getFrom(), is(nullValue()));

        rules = np.getSpec().getIngress().stream().filter(ing -> ing.getPorts().get(0).getPort().equals(new IntOrString(9094))).collect(Collectors.toList());
        assertThat(rules.size(), is(1));
        assertThat(rules.get(0).getFrom(), is(nullValue()));
    }

    @ParallelTest
    public void testGracePeriod() {
        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withNewTemplate()
                            .withNewPod()
                                .withTerminationGracePeriodSeconds(123)
                            .endPod()
                        .endTemplate()
                    .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        StatefulSet sts = kc.generateStatefulSet(true, null, null, null);
        assertThat(sts.getSpec().getTemplate().getSpec().getTerminationGracePeriodSeconds(), is(123L));
    }

    @ParallelTest
    public void testDefaultGracePeriod() {
        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        StatefulSet sts = kc.generateStatefulSet(true, null, null, null);
        assertThat(sts.getSpec().getTemplate().getSpec().getTerminationGracePeriodSeconds(), is(30L));
    }

    @ParallelTest
    public void testImagePullSecrets() {
        LocalObjectReference secret1 = new LocalObjectReference("some-pull-secret");
        LocalObjectReference secret2 = new LocalObjectReference("some-other-pull-secret");

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withNewTemplate()
                            .withNewPod()
                                .withImagePullSecrets(secret1, secret2)
                            .endPod()
                        .endTemplate()
                    .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        StatefulSet sts = kc.generateStatefulSet(true, null, null, null);
        assertThat(sts.getSpec().getTemplate().getSpec().getImagePullSecrets().size(), is(2));
        assertThat(sts.getSpec().getTemplate().getSpec().getImagePullSecrets().contains(secret1), is(true));
        assertThat(sts.getSpec().getTemplate().getSpec().getImagePullSecrets().contains(secret2), is(true));
    }

    @ParallelTest
    public void testImagePullSecretsFromCO() {
        LocalObjectReference secret1 = new LocalObjectReference("some-pull-secret");
        LocalObjectReference secret2 = new LocalObjectReference("some-other-pull-secret");

        List<LocalObjectReference> secrets = new ArrayList<>(2);
        secrets.add(secret1);
        secrets.add(secret2);

        Kafka kafkaAssembly = ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap());
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        StatefulSet sts = kc.generateStatefulSet(true, null, secrets, null);
        assertThat(sts.getSpec().getTemplate().getSpec().getImagePullSecrets().size(), is(2));
        assertThat(sts.getSpec().getTemplate().getSpec().getImagePullSecrets().contains(secret1), is(true));
        assertThat(sts.getSpec().getTemplate().getSpec().getImagePullSecrets().contains(secret2), is(true));
    }

    @ParallelTest
    public void testImagePullSecretsFromBoth() {
        LocalObjectReference secret1 = new LocalObjectReference("some-pull-secret");
        LocalObjectReference secret2 = new LocalObjectReference("some-other-pull-secret");

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withNewTemplate()
                                .withNewPod()
                                .withImagePullSecrets(secret2)
                                .endPod()
                        .endTemplate()
                    .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        StatefulSet sts = kc.generateStatefulSet(true, null, singletonList(secret1), null);
        assertThat(sts.getSpec().getTemplate().getSpec().getImagePullSecrets().size(), is(1));
        assertThat(sts.getSpec().getTemplate().getSpec().getImagePullSecrets().contains(secret1), is(false));
        assertThat(sts.getSpec().getTemplate().getSpec().getImagePullSecrets().contains(secret2), is(true));
    }

    @ParallelTest
    public void testDefaultImagePullSecrets() {
        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        StatefulSet sts = kc.generateStatefulSet(true, null, null, null);
        assertThat(sts.getSpec().getTemplate().getSpec().getImagePullSecrets(), is(nullValue()));
    }

    @ParallelTest
    public void testSecurityContext() {
        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withNewTemplate()
                            .withNewPod()
                                .withSecurityContext(new PodSecurityContextBuilder().withFsGroup(123L).withRunAsGroup(456L).withRunAsUser(789L).build())
                            .endPod()
                        .endTemplate()
                    .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        StatefulSet sts = kc.generateStatefulSet(true, null, null, null);
        assertThat(sts.getSpec().getTemplate().getSpec().getSecurityContext(), is(notNullValue()));
        assertThat(sts.getSpec().getTemplate().getSpec().getSecurityContext().getFsGroup(), is(123L));
        assertThat(sts.getSpec().getTemplate().getSpec().getSecurityContext().getRunAsGroup(), is(456L));
        assertThat(sts.getSpec().getTemplate().getSpec().getSecurityContext().getRunAsUser(), is(789L));
    }

    @ParallelTest
    public void testDefaultSecurityContext() {
        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        StatefulSet sts = kc.generateStatefulSet(true, null, null, null);
        assertThat(sts.getSpec().getTemplate().getSpec().getSecurityContext(), is(nullValue()));
    }

    @ParallelTest
    public void testPodDisruptionBudget() {
        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                    .withNewTemplate()
                        .withNewPodDisruptionBudget()
                            .withMaxUnavailable(2)
                        .endPodDisruptionBudget()
                    .endTemplate()
                    .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        PodDisruptionBudget pdb = kc.generatePodDisruptionBudget();
        assertThat(pdb.getSpec().getMaxUnavailable(), is(new IntOrString(2)));
    }

    @ParallelTest
    public void testPodDisruptionBudgetV1Beta1() {
        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                    .withNewTemplate()
                        .withNewPodDisruptionBudget()
                            .withMaxUnavailable(2)
                        .endPodDisruptionBudget()
                    .endTemplate()
                    .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        io.fabric8.kubernetes.api.model.policy.v1beta1.PodDisruptionBudget pdbV1Beta1 = kc.generatePodDisruptionBudgetV1Beta1();
        assertThat(pdbV1Beta1.getSpec().getMaxUnavailable(), is(new IntOrString(2)));
    }

    @ParallelTest
    public void testDefaultPodDisruptionBudget() {
        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        PodDisruptionBudget pdb = kc.generatePodDisruptionBudget();
        assertThat(pdb.getSpec().getMaxUnavailable(), is(new IntOrString(1)));
    }

    @ParallelTest
    public void testDefaultPodDisruptionBudgetV1Beta1() {
        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        io.fabric8.kubernetes.api.model.policy.v1beta1.PodDisruptionBudget pdbV1Beta1 = kc.generatePodDisruptionBudgetV1Beta1();
        assertThat(pdbV1Beta1.getSpec().getMaxUnavailable(), is(new IntOrString(1)));
    }

    @ParallelTest
    public void testImagePullPolicy() {
        Kafka kafkaAssembly = ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap());
        kafkaAssembly.getSpec().getKafka().setRack(new RackBuilder().withTopologyKey("topology-key").build());
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        StatefulSet sts = kc.generateStatefulSet(true, ImagePullPolicy.ALWAYS, null, null);
        assertThat(sts.getSpec().getTemplate().getSpec().getInitContainers().get(0).getImagePullPolicy(), is(ImagePullPolicy.ALWAYS.toString()));
        assertThat(sts.getSpec().getTemplate().getSpec().getContainers().get(0).getImagePullPolicy(), is(ImagePullPolicy.ALWAYS.toString()));

        sts = kc.generateStatefulSet(true, ImagePullPolicy.IFNOTPRESENT, null, null);
        assertThat(sts.getSpec().getTemplate().getSpec().getInitContainers().get(0).getImagePullPolicy(), is(ImagePullPolicy.IFNOTPRESENT.toString()));
        assertThat(sts.getSpec().getTemplate().getSpec().getContainers().get(0).getImagePullPolicy(), is(ImagePullPolicy.IFNOTPRESENT.toString()));
    }

    @ParallelTest
    public void testExternalServiceWithDualStackNetworking() {
        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withListeners(new GenericKafkaListenerBuilder()
                                    .withName("np")
                                    .withPort(9094)
                                    .withType(KafkaListenerType.NODEPORT)
                                    .withTls(true)
                                    .withNewConfiguration()
                                        .withIpFamilyPolicy(IpFamilyPolicy.PREFER_DUAL_STACK)
                                        .withIpFamilies(IpFamily.IPV6, IpFamily.IPV4)
                                    .endConfiguration()
                                    .build(),
                                new GenericKafkaListenerBuilder()
                                    .withName("lb")
                                    .withPort(9095)
                                    .withType(KafkaListenerType.LOADBALANCER)
                                    .withTls(true)
                                    .withNewConfiguration()
                                        .withIpFamilyPolicy(IpFamilyPolicy.PREFER_DUAL_STACK)
                                        .withIpFamilies(IpFamily.IPV6, IpFamily.IPV4)
                                    .endConfiguration()
                                    .build())
                    .endKafka()
                .endSpec()
                .build();

        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        List<Service> services = new ArrayList<>();
        services.addAll(kc.generateExternalBootstrapServices());
        services.addAll(kc.generateExternalServices(0));
        services.addAll(kc.generateExternalServices(1));
        services.addAll(kc.generateExternalServices(2));

        for (Service svc : services)    {
            assertThat(svc.getSpec().getIpFamilyPolicy(), is("PreferDualStack"));
            assertThat(svc.getSpec().getIpFamilies(), contains("IPv6", "IPv4"));
        }
    }

    @ParallelTest
    public void testGetExternalServiceAdvertisedHostAndPortOverride() {
        GenericKafkaListenerConfigurationBroker nodePortListenerBrokerConfig0 = new GenericKafkaListenerConfigurationBroker();
        nodePortListenerBrokerConfig0.setBroker(0);
        nodePortListenerBrokerConfig0.setAdvertisedHost("my-host-0.cz");
        nodePortListenerBrokerConfig0.setAdvertisedPort(10000);

        GenericKafkaListenerConfigurationBroker nodePortListenerBrokerConfig1 = new GenericKafkaListenerConfigurationBroker();
        nodePortListenerBrokerConfig1.setBroker(1);
        nodePortListenerBrokerConfig1.setAdvertisedHost("my-host-1.cz");
        nodePortListenerBrokerConfig1.setAdvertisedPort(10001);

        GenericKafkaListenerConfigurationBroker nodePortListenerBrokerConfig2 = new GenericKafkaListenerConfigurationBroker();
        nodePortListenerBrokerConfig2.setBroker(2);
        nodePortListenerBrokerConfig2.setAdvertisedHost("my-host-2.cz");
        nodePortListenerBrokerConfig2.setAdvertisedPort(10002);

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withListeners(new GenericKafkaListenerBuilder()
                                .withName("external")
                                .withPort(9094)
                                .withType(KafkaListenerType.NODEPORT)
                                .withTls(true)
                                .withNewConfiguration()
                                    .withBrokers(nodePortListenerBrokerConfig0, nodePortListenerBrokerConfig1, nodePortListenerBrokerConfig2)
                                .endConfiguration()
                                .build())
                    .endKafka()
                .endSpec()
                .build();

        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        assertThat(ListenersUtils.brokerAdvertisedPort(kc.getListeners().get(0), 0), is(10000));
        assertThat(ListenersUtils.brokerAdvertisedHost(kc.getListeners().get(0), 0), is("my-host-0.cz"));

        assertThat(ListenersUtils.brokerAdvertisedPort(kc.getListeners().get(0), 1), is(10001));
        assertThat(ListenersUtils.brokerAdvertisedHost(kc.getListeners().get(0), 1), is("my-host-1.cz"));

        assertThat(ListenersUtils.brokerAdvertisedPort(kc.getListeners().get(0), 2), is(10002));
        assertThat(ListenersUtils.brokerAdvertisedHost(kc.getListeners().get(0), 2), is("my-host-2.cz"));
    }

    @ParallelTest
    public void testGetExternalServiceWithoutAdvertisedHostAndPortOverride() {
        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withListeners(new GenericKafkaListenerBuilder()
                                .withName("external")
                                .withPort(9094)
                                .withType(KafkaListenerType.NODEPORT)
                                .withTls(true)
                                .build())
                    .endKafka()
                .endSpec()
                .build();

        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        assertThat(ListenersUtils.brokerAdvertisedPort(kc.getListeners().get(0), 0), is(nullValue()));
        assertThat(ListenersUtils.brokerAdvertisedHost(kc.getListeners().get(0), 0), is(nullValue()));

        assertThat(ListenersUtils.brokerAdvertisedPort(kc.getListeners().get(0), 1), is(nullValue()));
        assertThat(ListenersUtils.brokerAdvertisedHost(kc.getListeners().get(0), 1), is(nullValue()));

        assertThat(ListenersUtils.brokerAdvertisedPort(kc.getListeners().get(0), 2), is(nullValue()));
        assertThat(ListenersUtils.brokerAdvertisedHost(kc.getListeners().get(0), 2), is(nullValue()));
    }

    @ParallelTest
    public void testGetExternalAdvertisedUrlWithOverrides() {
        GenericKafkaListenerConfigurationBroker nodePortListenerBrokerConfig0 = new GenericKafkaListenerConfigurationBroker();
        nodePortListenerBrokerConfig0.setBroker(0);
        nodePortListenerBrokerConfig0.setAdvertisedHost("my-host-0.cz");
        nodePortListenerBrokerConfig0.setAdvertisedPort(10000);

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withListeners(new GenericKafkaListenerBuilder()
                                .withName("external")
                                .withPort(9094)
                                .withType(KafkaListenerType.NODEPORT)
                                .withTls(true)
                                .withNewConfiguration()
                                    .withBrokers(nodePortListenerBrokerConfig0)
                                .endConfiguration()
                                .build())
                    .endKafka()
                .endSpec()
                .build();

        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        assertThat(kc.getAdvertisedHostname(kc.getListeners().get(0), 0, "some-host.com"), is("my-host-0.cz"));
        assertThat(kc.getAdvertisedHostname(kc.getListeners().get(0), 0, ""), is("my-host-0.cz"));
        assertThat(kc.getAdvertisedHostname(kc.getListeners().get(0), 1, "some-host.com"), is("some-host.com"));
        assertThat(kc.getAdvertisedHostname(kc.getListeners().get(0), 1, ""), is(""));

        assertThat(kc.getAdvertisedPort(kc.getListeners().get(0), 0, 12345), is("10000"));
        assertThat(kc.getAdvertisedPort(kc.getListeners().get(0), 0, 12345), is("10000"));
        assertThat(kc.getAdvertisedPort(kc.getListeners().get(0), 1, 12345), is("12345"));
        assertThat(kc.getAdvertisedPort(kc.getListeners().get(0), 1, 12345), is("12345"));
    }

    @ParallelTest
    public void testGetExternalAdvertisedUrlWithoutOverrides() {
        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withListeners(new GenericKafkaListenerBuilder()
                                .withName("external")
                                .withPort(9094)
                                .withType(KafkaListenerType.NODEPORT)
                                .withTls(true)
                                .build())
                    .endKafka()
                .endSpec()
                .build();

        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        assertThat(kc.getAdvertisedHostname(kc.getListeners().get(0), 0, "some-host.com"), is("some-host.com"));
        assertThat(kc.getAdvertisedHostname(kc.getListeners().get(0), 0, ""), is(""));

        assertThat(kc.getAdvertisedPort(kc.getListeners().get(0), 0, 12345), is("12345"));
        assertThat(kc.getAdvertisedPort(kc.getListeners().get(0), 0, 12345), is("12345"));
    }

    @ParallelTest
    public void testGeneratePersistentVolumeClaimsPersistentWithClaimDeletion() {
        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withNewPersistentClaimStorage()
                            .withStorageClass("gp2-ssd")
                            .withDeleteClaim(true)
                            .withSize("100Gi")
                        .endPersistentClaimStorage()
                    .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        // Check Storage annotation on STS
        assertThat(kc.generateStatefulSet(true, ImagePullPolicy.NEVER, null, null).getMetadata().getAnnotations().get(AbstractModel.ANNO_STRIMZI_IO_STORAGE),
                is(ModelUtils.encodeStorageToJson(kafkaAssembly.getSpec().getKafka().getStorage())));

        // Check PVCs
        List<PersistentVolumeClaim> pvcs = kc.generatePersistentVolumeClaims(kc.getStorage());

        assertThat(pvcs.size(), is(3));

        for (PersistentVolumeClaim pvc : pvcs) {
            assertThat(pvc.getSpec().getResources().getRequests().get("storage"), is(new Quantity("100Gi")));
            assertThat(pvc.getSpec().getStorageClassName(), is("gp2-ssd"));
            assertThat(pvc.getMetadata().getName().startsWith(KafkaCluster.VOLUME_NAME), is(true));
            assertThat(pvc.getMetadata().getOwnerReferences().size(), is(1));
            assertThat(pvc.getMetadata().getAnnotations().get(AbstractModel.ANNO_STRIMZI_IO_DELETE_CLAIM), is("true"));
        }
    }

    @ParallelTest
    public void testGeneratePersistentVolumeClaimsPersistentWithoutClaimDeletion() {
        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                .editKafka()
                .withNewPersistentClaimStorage().withStorageClass("gp2-ssd").withDeleteClaim(false).withSize("100Gi").endPersistentClaimStorage()
                .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        // Check Storage annotation on STS
        assertThat(kc.generateStatefulSet(true, ImagePullPolicy.NEVER, null, null).getMetadata().getAnnotations().get(AbstractModel.ANNO_STRIMZI_IO_STORAGE),
                is(ModelUtils.encodeStorageToJson(kafkaAssembly.getSpec().getKafka().getStorage())));

        // Check PVCs
        List<PersistentVolumeClaim> pvcs = kc.generatePersistentVolumeClaims(kc.getStorage());

        assertThat(pvcs.size(), is(3));

        for (PersistentVolumeClaim pvc : pvcs) {
            assertThat(pvc.getSpec().getResources().getRequests().get("storage"), is(new Quantity("100Gi")));
            assertThat(pvc.getSpec().getStorageClassName(), is("gp2-ssd"));
            assertThat(pvc.getMetadata().getName().startsWith(KafkaCluster.VOLUME_NAME), is(true));
            assertThat(pvc.getMetadata().getOwnerReferences().size(), is(0));
            assertThat(pvc.getMetadata().getAnnotations().get(AbstractModel.ANNO_STRIMZI_IO_DELETE_CLAIM), is("false"));
        }
    }

    @ParallelTest
    public void testGeneratePersistentVolumeClaimsPersistentWithOverride() {
        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                .editKafka()
                .withNewPersistentClaimStorage()
                    .withStorageClass("gp2-ssd")
                    .withDeleteClaim(false)
                    .withSize("100Gi")
                    .withOverrides(new PersistentClaimStorageOverrideBuilder()
                            .withBroker(1)
                            .withStorageClass("gp2-ssd-az1")
                            .build())
                .endPersistentClaimStorage()
                .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        // Check Storage annotation on STS
        assertThat(kc.generateStatefulSet(true, ImagePullPolicy.NEVER, null, null).getMetadata().getAnnotations().get(AbstractModel.ANNO_STRIMZI_IO_STORAGE),
                is(ModelUtils.encodeStorageToJson(kafkaAssembly.getSpec().getKafka().getStorage())));

        // Check PVCs
        List<PersistentVolumeClaim> pvcs = kc.generatePersistentVolumeClaims(kc.getStorage());

        assertThat(pvcs.size(), is(3));

        for (int i = 0; i < 3; i++) {
            PersistentVolumeClaim pvc = pvcs.get(i);

            assertThat(pvc.getSpec().getResources().getRequests().get("storage"), is(new Quantity("100Gi")));

            if (i != 1) {
                assertThat(pvc.getSpec().getStorageClassName(), is("gp2-ssd"));
            } else {
                assertThat(pvc.getSpec().getStorageClassName(), is("gp2-ssd-az1"));
            }

            assertThat(pvc.getMetadata().getName().startsWith(KafkaCluster.VOLUME_NAME), is(true));
            assertThat(pvc.getMetadata().getOwnerReferences().size(), is(0));
            assertThat(pvc.getMetadata().getAnnotations().get(AbstractModel.ANNO_STRIMZI_IO_DELETE_CLAIM), is("false"));
        }
    }

    @ParallelTest
    public void testGeneratePersistentVolumeClaimsJbod() {
        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                .editKafka()
                .withStorage(new JbodStorageBuilder().withVolumes(
                        new PersistentClaimStorageBuilder().withStorageClass("gp2-ssd")
                                .withDeleteClaim(false)
                                .withId(0)
                                .withSize("100Gi")
                                .withOverrides(new PersistentClaimStorageOverrideBuilder().withBroker(1).withStorageClass("gp2-ssd-az1").build())
                                .build(),
                        new PersistentClaimStorageBuilder()
                                .withStorageClass("gp2-st1")
                                .withDeleteClaim(true)
                                .withId(1)
                                .withSize("1000Gi")
                                .withOverrides(new PersistentClaimStorageOverrideBuilder().withBroker(1).withStorageClass("gp2-st1-az1").build())
                                .build())
                        .build())
                .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        // Check Storage annotation on STS
        assertThat(kc.generateStatefulSet(true, ImagePullPolicy.NEVER, null, null).getMetadata().getAnnotations().get(AbstractModel.ANNO_STRIMZI_IO_STORAGE),
                is(ModelUtils.encodeStorageToJson(kafkaAssembly.getSpec().getKafka().getStorage())));

        // Check PVCs
        List<PersistentVolumeClaim> pvcs = kc.generatePersistentVolumeClaims(kc.getStorage());

        assertThat(pvcs.size(), is(6));

        for (int i = 0; i < 3; i++) {
            PersistentVolumeClaim pvc = pvcs.get(i);
            assertThat(pvc.getSpec().getResources().getRequests().get("storage"), is(new Quantity("100Gi")));

            if (i != 1) {
                assertThat(pvc.getSpec().getStorageClassName(), is("gp2-ssd"));
            } else {
                assertThat(pvc.getSpec().getStorageClassName(), is("gp2-ssd-az1"));
            }

            assertThat(pvc.getMetadata().getName().startsWith(KafkaCluster.VOLUME_NAME), is(true));
            assertThat(pvc.getMetadata().getOwnerReferences().size(), is(0));
            assertThat(pvc.getMetadata().getAnnotations().get(AbstractModel.ANNO_STRIMZI_IO_DELETE_CLAIM), is("false"));
        }

        for (int i = 3; i < 6; i++) {
            PersistentVolumeClaim pvc = pvcs.get(i);
            assertThat(pvc.getSpec().getResources().getRequests().get("storage"), is(new Quantity("1000Gi")));

            if (i != 4) {
                assertThat(pvc.getSpec().getStorageClassName(), is("gp2-st1"));
            } else {
                assertThat(pvc.getSpec().getStorageClassName(), is("gp2-st1-az1"));
            }

            assertThat(pvc.getMetadata().getName().startsWith(KafkaCluster.VOLUME_NAME), is(true));
            assertThat(pvc.getMetadata().getOwnerReferences().size(), is(1));
            assertThat(pvc.getMetadata().getAnnotations().get(AbstractModel.ANNO_STRIMZI_IO_DELETE_CLAIM), is("true"));
        }
    }

    @ParallelTest
    public void testGeneratePersistentVolumeClaimsJbodWithTemplate() {
        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withNewTemplate()
                            .withNewPersistentVolumeClaim()
                                .withNewMetadata()
                                    .withLabels(singletonMap("testLabel", "testValue"))
                                    .withAnnotations(singletonMap("testAnno", "testValue"))
                                .endMetadata()
                            .endPersistentVolumeClaim()
                        .endTemplate()
                        .withStorage(new JbodStorageBuilder().withVolumes(
                            new PersistentClaimStorageBuilder().withStorageClass("gp2-ssd")
                                    .withDeleteClaim(false)
                                    .withId(0)
                                    .withSize("100Gi")
                                    .withOverrides(new PersistentClaimStorageOverrideBuilder().withBroker(1).withStorageClass("gp2-ssd-az1").build())
                                    .build(),
                            new PersistentClaimStorageBuilder()
                                    .withStorageClass("gp2-st1")
                                    .withDeleteClaim(true)
                                    .withId(1)
                                    .withSize("1000Gi")
                                    .withOverrides(new PersistentClaimStorageOverrideBuilder().withBroker(1).withStorageClass("gp2-st1-az1").build())
                                    .build())
                            .build())
                    .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        // Check PVCs
        List<PersistentVolumeClaim> pvcs = kc.generatePersistentVolumeClaims(kc.getStorage());

        assertThat(pvcs.size(), is(6));

        for (int i = 0; i < 6; i++) {
            PersistentVolumeClaim pvc = pvcs.get(i);
            assertThat(pvc.getMetadata().getLabels().get("testLabel"), is("testValue"));
            assertThat(pvc.getMetadata().getAnnotations().get("testAnno"), is("testValue"));
        }
    }

    @ParallelTest
    public void testGeneratePersistentVolumeClaimsJbodWithOverrides() {
        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                .editKafka()
                .withStorage(new JbodStorageBuilder().withVolumes(
                        new PersistentClaimStorageBuilder().withStorageClass("gp2-ssd").withDeleteClaim(false).withId(0).withSize("100Gi").build(),
                        new PersistentClaimStorageBuilder().withStorageClass("gp2-st1").withDeleteClaim(true).withId(1).withSize("1000Gi").build())
                        .build())
                .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        // Check Storage annotation on STS
        assertThat(kc.generateStatefulSet(true, ImagePullPolicy.NEVER, null, null).getMetadata().getAnnotations().get(AbstractModel.ANNO_STRIMZI_IO_STORAGE),
                is(ModelUtils.encodeStorageToJson(kafkaAssembly.getSpec().getKafka().getStorage())));

        // Check PVCs
        List<PersistentVolumeClaim> pvcs = kc.generatePersistentVolumeClaims(kc.getStorage());

        assertThat(pvcs.size(), is(6));

        for (int i = 0; i < 3; i++) {
            PersistentVolumeClaim pvc = pvcs.get(i);
            assertThat(pvc.getSpec().getResources().getRequests().get("storage"), is(new Quantity("100Gi")));
            assertThat(pvc.getSpec().getStorageClassName(), is("gp2-ssd"));
            assertThat(pvc.getMetadata().getName().startsWith(KafkaCluster.VOLUME_NAME), is(true));
            assertThat(pvc.getMetadata().getOwnerReferences().size(), is(0));
            assertThat(pvc.getMetadata().getAnnotations().get(AbstractModel.ANNO_STRIMZI_IO_DELETE_CLAIM), is("false"));
        }

        for (int i = 3; i < 6; i++) {
            PersistentVolumeClaim pvc = pvcs.get(i);
            assertThat(pvc.getSpec().getResources().getRequests().get("storage"), is(new Quantity("1000Gi")));
            assertThat(pvc.getSpec().getStorageClassName(), is("gp2-st1"));
            assertThat(pvc.getMetadata().getName().startsWith(KafkaCluster.VOLUME_NAME), is(true));
            assertThat(pvc.getMetadata().getOwnerReferences().size(), is(1));
            assertThat(pvc.getMetadata().getAnnotations().get(AbstractModel.ANNO_STRIMZI_IO_DELETE_CLAIM), is("true"));
        }
    }

    @ParallelTest
    public void testGeneratePersistentVolumeClaimsJbodWithoutVolumes() {
        assertThrows(InvalidResourceException.class, () -> {
            Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                    image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                    .editSpec()
                    .editKafka()
                    .withStorage(new JbodStorageBuilder().withVolumes(List.of())
                            .build())
                    .endKafka()
                    .endSpec()
                    .build();
            KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);
        });
    }

    @ParallelTest
    public void testStorageValidationAfterInitialDeployment() {
        assertThrows(InvalidResourceException.class, () -> {
            Storage oldStorage = new JbodStorageBuilder()
                    .withVolumes(new PersistentClaimStorageBuilder().withSize("100Gi").build())
                    .build();

            Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                    image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                    .editSpec()
                    .editKafka()
                    .withStorage(new JbodStorageBuilder().withVolumes(List.of())
                            .build())
                    .endKafka()
                    .endSpec()
                    .build();
            KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS, oldStorage, replicas, false);
        });
    }

    @ParallelTest
    public void testGeneratePersistentVolumeClaimsEphemeral()    {
        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withNewEphemeralStorage().endEphemeralStorage()
                    .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        // Check Storage annotation on STS
        assertThat(kc.generateStatefulSet(true, ImagePullPolicy.NEVER, null, null).getMetadata().getAnnotations().get(AbstractModel.ANNO_STRIMZI_IO_STORAGE),
                is(ModelUtils.encodeStorageToJson(kafkaAssembly.getSpec().getKafka().getStorage())));

        // Check PVCs
        List<PersistentVolumeClaim> pvcs = kc.generatePersistentVolumeClaims(kc.getStorage());

        assertThat(pvcs.size(), is(0));
    }

    @ParallelTest
    public void testStorageReverting() {
        Storage jbod = new JbodStorageBuilder().withVolumes(
                new PersistentClaimStorageBuilder().withStorageClass("gp2-ssd").withDeleteClaim(false).withId(0).withSize("100Gi").build(),
                new PersistentClaimStorageBuilder().withStorageClass("gp2-st1").withDeleteClaim(true).withId(1).withSize("1000Gi").build())
                .build();

        Storage ephemeral = new EphemeralStorageBuilder().build();

        Storage persistent = new PersistentClaimStorageBuilder().withStorageClass("gp2-ssd").withDeleteClaim(false).withId(0).withSize("100Gi").build();

        // Test Storage changes and how the are reverted

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withStorage(jbod)
                        .withConfig(Map.of("default.replication.factor", 3, "min.insync.replicas", 2))
                    .endKafka()
                .endSpec()
                .build();
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS, ephemeral, replicas, false);

        // Storage is reverted
        assertThat(kc.getStorage(), is(ephemeral));

        // Warning status condition is set
        assertThat(kc.getWarningConditions().size(), is(1));
        assertThat(kc.getWarningConditions().get(0).getReason(), is("KafkaStorage"));

        kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withStorage(jbod)
                        .withConfig(Map.of("default.replication.factor", 3, "min.insync.replicas", 2))
                    .endKafka()
                .endSpec()
                .build();
        kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS, persistent, replicas, false);

        // Storage is reverted
        assertThat(kc.getStorage(), is(persistent));

        // Warning status condition is set
        assertThat(kc.getWarningConditions().size(), is(1));
        assertThat(kc.getWarningConditions().get(0).getReason(), is("KafkaStorage"));

        kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withStorage(ephemeral)
                        .withConfig(Map.of("default.replication.factor", 3, "min.insync.replicas", 2))
                    .endKafka()
                .endSpec()
                .build();
        kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS, jbod, replicas, false);

        // Storage is reverted
        assertThat(kc.getStorage(), is(jbod));

        // Warning status condition is set
        assertThat(kc.getWarningConditions().size(), is(1));
        assertThat(kc.getWarningConditions().get(0).getReason(), is("KafkaStorage"));

        kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withStorage(persistent)
                        .withConfig(Map.of("default.replication.factor", 3, "min.insync.replicas", 2))
                    .endKafka()
                .endSpec()
                .build();
        kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS, jbod, replicas, false);

        // Storage is reverted
        assertThat(kc.getStorage(), is(jbod));

        // Warning status condition is set
        assertThat(kc.getWarningConditions().size(), is(1));
        assertThat(kc.getWarningConditions().get(0).getReason(), is("KafkaStorage"));
    }

    @ParallelTest
    public void testExternalIngress() {
        GenericKafkaListenerConfigurationBroker broker0 = new GenericKafkaListenerConfigurationBrokerBuilder()
                .withHost("my-broker-kafka-0.com")
                .withLabels(Collections.singletonMap("label", "label-value"))
                .withAnnotations(Collections.singletonMap("dns-annotation", "my-kafka-broker.com"))
                .withBroker(0)
                .build();

        GenericKafkaListenerConfigurationBroker broker1 = new GenericKafkaListenerConfigurationBrokerBuilder()
                .withHost("my-broker-kafka-1.com")
                .withLabels(Collections.singletonMap("label", "label-value"))
                .withAnnotations(Collections.singletonMap("dns-annotation", "my-kafka-broker.com"))
                .withBroker(1)
                .build();

        GenericKafkaListenerConfigurationBroker broker2 = new GenericKafkaListenerConfigurationBrokerBuilder()
                .withHost("my-broker-kafka-2.com")
                .withLabels(Collections.singletonMap("label", "label-value"))
                .withAnnotations(Collections.singletonMap("dns-annotation", "my-kafka-broker.com"))
                .withBroker(2)
                .build();

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withListeners(new GenericKafkaListenerBuilder()
                                .withName("external")
                                .withPort(9094)
                                .withType(KafkaListenerType.INGRESS)
                                .withTls(true)
                                .withNewConfiguration()
                                    .withNewBootstrap()
                                        .withHost("my-kafka-bootstrap.com")
                                        .withAnnotations(Collections.singletonMap("dns-annotation", "my-kafka-bootstrap.com"))
                                        .withLabels(Collections.singletonMap("label", "label-value"))
                                    .endBootstrap()
                                    .withBrokers(broker0, broker1, broker2)
                                .endConfiguration()
                                .build())
                    .endKafka()
                .endSpec()
                .build();

        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        assertThat(kc.isExposedWithIngress(), is(true));

        // Check StatefulSet changes
        StatefulSet sts = kc.generateStatefulSet(true, null, null, null);

        List<ContainerPort> ports = sts.getSpec().getTemplate().getSpec().getContainers().get(0).getPorts();
        assertThat(ports.contains(kc.createContainerPort(ListenersUtils.BACKWARDS_COMPATIBLE_EXTERNAL_PORT_NAME, 9094, "TCP")), is(true));

        // Check external bootstrap service
        Service ext = kc.generateExternalBootstrapServices().get(0);
        assertThat(ext.getMetadata().getName(), is(KafkaResources.externalBootstrapServiceName(cluster)));
        assertThat(ext.getSpec().getType(), is("ClusterIP"));
        assertThat(ext.getSpec().getSelector(), is(kc.getSelectorLabels().toMap()));
        assertThat(ext.getSpec().getPorts(), is(Collections.singletonList(kc.createServicePort(ListenersUtils.BACKWARDS_COMPATIBLE_EXTERNAL_PORT_NAME, 9094, 9094, "TCP"))));
        checkOwnerReference(kc.createOwnerReference(), ext);

        // Check per pod services
        for (int i = 0; i < replicas; i++)  {
            Service srv = kc.generateExternalServices(i).get(0);
            assertThat(srv.getMetadata().getName(), is(KafkaResources.kafkaStatefulSetName(cluster) + "-" + i));
            assertThat(srv.getSpec().getType(), is("ClusterIP"));
            assertThat(srv.getSpec().getSelector().get(Labels.KUBERNETES_STATEFULSET_POD_LABEL), is(KafkaResources.kafkaPodName(cluster, i)));
            assertThat(srv.getSpec().getPorts(), is(Collections.singletonList(kc.createServicePort(ListenersUtils.BACKWARDS_COMPATIBLE_EXTERNAL_PORT_NAME, 9094, 9094, "TCP"))));
            checkOwnerReference(kc.createOwnerReference(), srv);
        }

        // Check bootstrap ingress
        Ingress bing = kc.generateExternalBootstrapIngresses().get(0);
        assertThat(bing.getMetadata().getName(), is(KafkaResources.bootstrapServiceName(cluster)));
        assertThat(bing.getSpec().getIngressClassName(), is(nullValue()));
        assertThat(bing.getMetadata().getAnnotations().get("dns-annotation"), is("my-kafka-bootstrap.com"));
        assertThat(bing.getMetadata().getLabels().get("label"), is("label-value"));
        assertThat(bing.getSpec().getTls().size(), is(1));
        assertThat(bing.getSpec().getTls().get(0).getHosts().size(), is(1));
        assertThat(bing.getSpec().getTls().get(0).getHosts().get(0), is("my-kafka-bootstrap.com"));
        assertThat(bing.getSpec().getRules().size(), is(1));
        assertThat(bing.getSpec().getRules().get(0).getHost(), is("my-kafka-bootstrap.com"));
        assertThat(bing.getSpec().getRules().get(0).getHttp().getPaths().size(), is(1));
        assertThat(bing.getSpec().getRules().get(0).getHttp().getPaths().get(0).getPath(), is("/"));
        assertThat(bing.getSpec().getRules().get(0).getHttp().getPaths().get(0).getBackend().getService().getName(), is(KafkaResources.externalBootstrapServiceName(cluster)));
        assertThat(bing.getSpec().getRules().get(0).getHttp().getPaths().get(0).getBackend().getService().getPort().getNumber(), is(9094));
        checkOwnerReference(kc.createOwnerReference(), bing);

        io.fabric8.kubernetes.api.model.networking.v1beta1.Ingress bingV1Beta1 = kc.generateExternalBootstrapIngressesV1Beta1().get(0);
        assertThat(bingV1Beta1.getMetadata().getName(), is(KafkaResources.bootstrapServiceName(cluster)));
        assertThat(bingV1Beta1.getSpec().getIngressClassName(), is(nullValue()));
        assertThat(bingV1Beta1.getMetadata().getAnnotations().get("dns-annotation"), is("my-kafka-bootstrap.com"));
        assertThat(bingV1Beta1.getMetadata().getLabels().get("label"), is("label-value"));
        assertThat(bingV1Beta1.getSpec().getTls().size(), is(1));
        assertThat(bingV1Beta1.getSpec().getTls().get(0).getHosts().size(), is(1));
        assertThat(bingV1Beta1.getSpec().getTls().get(0).getHosts().get(0), is("my-kafka-bootstrap.com"));
        assertThat(bingV1Beta1.getSpec().getRules().size(), is(1));
        assertThat(bingV1Beta1.getSpec().getRules().get(0).getHost(), is("my-kafka-bootstrap.com"));
        assertThat(bingV1Beta1.getSpec().getRules().get(0).getHttp().getPaths().size(), is(1));
        assertThat(bingV1Beta1.getSpec().getRules().get(0).getHttp().getPaths().get(0).getPath(), is("/"));
        assertThat(bingV1Beta1.getSpec().getRules().get(0).getHttp().getPaths().get(0).getBackend().getServiceName(), is(KafkaResources.externalBootstrapServiceName(cluster)));
        assertThat(bingV1Beta1.getSpec().getRules().get(0).getHttp().getPaths().get(0).getBackend().getServicePort(), is(new IntOrString(9094)));
        checkOwnerReference(kc.createOwnerReference(), bingV1Beta1);

        // Check per pod ingress
        for (int i = 0; i < replicas; i++)  {
            Ingress ing = kc.generateExternalIngresses(i).get(0);
            assertThat(ing.getMetadata().getName(), is(KafkaResources.kafkaStatefulSetName(cluster) + "-" + i));
            assertThat(ing.getSpec().getIngressClassName(), is(nullValue()));
            assertThat(ing.getMetadata().getAnnotations().get("dns-annotation"), is("my-kafka-broker.com"));
            assertThat(ing.getMetadata().getLabels().get("label"), is("label-value"));
            assertThat(ing.getSpec().getTls().size(), is(1));
            assertThat(ing.getSpec().getTls().get(0).getHosts().size(), is(1));
            assertThat(ing.getSpec().getTls().get(0).getHosts().get(0), is(String.format("my-broker-kafka-%d.com", i)));
            assertThat(ing.getSpec().getRules().size(), is(1));
            assertThat(ing.getSpec().getRules().get(0).getHost(), is(String.format("my-broker-kafka-%d.com", i)));
            assertThat(ing.getSpec().getRules().get(0).getHttp().getPaths().size(), is(1));
            assertThat(ing.getSpec().getRules().get(0).getHttp().getPaths().get(0).getPath(), is("/"));
            assertThat(ing.getSpec().getRules().get(0).getHttp().getPaths().get(0).getBackend().getService().getName(), is(KafkaResources.kafkaStatefulSetName(cluster) + "-" + i));
            assertThat(ing.getSpec().getRules().get(0).getHttp().getPaths().get(0).getBackend().getService().getPort().getNumber(), is(9094));
            checkOwnerReference(kc.createOwnerReference(), ing);

            io.fabric8.kubernetes.api.model.networking.v1beta1.Ingress ingV1Beta1 = kc.generateExternalIngressesV1Beta1(i).get(0);
            assertThat(ingV1Beta1.getMetadata().getName(), is(KafkaResources.kafkaStatefulSetName(cluster) + "-" + i));
            assertThat(ingV1Beta1.getSpec().getIngressClassName(), is(nullValue()));
            assertThat(ingV1Beta1.getMetadata().getAnnotations().get("dns-annotation"), is("my-kafka-broker.com"));
            assertThat(ingV1Beta1.getMetadata().getLabels().get("label"), is("label-value"));
            assertThat(ingV1Beta1.getSpec().getTls().size(), is(1));
            assertThat(ingV1Beta1.getSpec().getTls().get(0).getHosts().size(), is(1));
            assertThat(ingV1Beta1.getSpec().getTls().get(0).getHosts().get(0), is(String.format("my-broker-kafka-%d.com", i)));
            assertThat(ingV1Beta1.getSpec().getRules().size(), is(1));
            assertThat(ingV1Beta1.getSpec().getRules().get(0).getHost(), is(String.format("my-broker-kafka-%d.com", i)));
            assertThat(ingV1Beta1.getSpec().getRules().get(0).getHttp().getPaths().size(), is(1));
            assertThat(ingV1Beta1.getSpec().getRules().get(0).getHttp().getPaths().get(0).getPath(), is("/"));
            assertThat(ingV1Beta1.getSpec().getRules().get(0).getHttp().getPaths().get(0).getBackend().getServiceName(), is(KafkaResources.kafkaStatefulSetName(cluster) + "-" + i));
            assertThat(ingV1Beta1.getSpec().getRules().get(0).getHttp().getPaths().get(0).getBackend().getServicePort(), is(new IntOrString(9094)));
            checkOwnerReference(kc.createOwnerReference(), ingV1Beta1);
        }
    }

    @ParallelTest
    public void testExternalIngressClass() {
        GenericKafkaListenerConfigurationBroker broker0 = new GenericKafkaListenerConfigurationBrokerBuilder()
                .withHost("my-broker-kafka-0.com")
                .withBroker(0)
                .build();

        GenericKafkaListenerConfigurationBroker broker1 = new GenericKafkaListenerConfigurationBrokerBuilder()
                .withHost("my-broker-kafka-1.com")
                .withBroker(1)
                .build();

        GenericKafkaListenerConfigurationBroker broker2 = new GenericKafkaListenerConfigurationBrokerBuilder()
                .withHost("my-broker-kafka-2.com")
                .withBroker(2)
                .build();

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withListeners(new GenericKafkaListenerBuilder()
                                .withName("external")
                                .withPort(9094)
                                .withType(KafkaListenerType.INGRESS)
                                .withTls(true)
                                .withNewConfiguration()
                                    .withIngressClass("nginx-internal")
                                    .withNewBootstrap()
                                        .withHost("my-kafka-bootstrap.com")
                                        .withAnnotations(Collections.singletonMap("dns-annotation", "my-kafka-bootstrap.com"))
                                    .endBootstrap()
                                    .withBrokers(broker0, broker1, broker2)
                                .endConfiguration()
                                .build())
                    .endKafka()
                .endSpec()
                .build();

        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

         // Check bootstrap ingress
        Ingress bing = kc.generateExternalBootstrapIngresses().get(0);
        assertThat(bing.getSpec().getIngressClassName(), is("nginx-internal"));

        io.fabric8.kubernetes.api.model.networking.v1beta1.Ingress bingV1Beta1 = kc.generateExternalBootstrapIngressesV1Beta1().get(0);
        assertThat(bingV1Beta1.getSpec().getIngressClassName(), is("nginx-internal"));

        // Check per pod ingress
        for (int i = 0; i < replicas; i++)  {
            Ingress ing = kc.generateExternalIngresses(i).get(0);
            assertThat(ing.getSpec().getIngressClassName(), is("nginx-internal"));

            io.fabric8.kubernetes.api.model.networking.v1beta1.Ingress ingV1Beta1 = kc.generateExternalIngressesV1Beta1(i).get(0);
            assertThat(ingV1Beta1.getSpec().getIngressClassName(), is("nginx-internal"));
        }
    }

    @ParallelTest
    public void testExternalIngressMissingConfiguration() {
        GenericKafkaListenerConfigurationBroker broker0 = new GenericKafkaListenerConfigurationBrokerBuilder()
                .withBroker(0)
                .build();

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withListeners(new GenericKafkaListenerBuilder()
                                .withName("external")
                                .withPort(9094)
                                .withType(KafkaListenerType.INGRESS)
                                .withTls(true)
                                .withNewConfiguration()
                                    .withIngressClass("nginx-internal")
                                    .withNewBootstrap()
                                        .withHost("my-kafka-bootstrap.com")
                                        .withAnnotations(Collections.singletonMap("dns-annotation", "my-kafka-bootstrap.com"))
                                    .endBootstrap()
                                    .withBrokers(broker0)
                                .endConfiguration()
                                .build())
                    .endKafka()
                .endSpec()
                .build();

        assertThrows(InvalidResourceException.class, () -> KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS));
    }

    @ParallelTest
    public void testClusterRoleBindingNodePort() {
        String testNamespace = "other-namespace";

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(testNamespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withListeners(new GenericKafkaListenerBuilder()
                                .withName("external")
                                .withPort(9094)
                                .withType(KafkaListenerType.NODEPORT)
                                .withTls(true)
                                .withNewKafkaListenerAuthenticationTlsAuth()
                                .endKafkaListenerAuthenticationTlsAuth()
                                .build())
                    .endKafka()
                .endSpec()
                .build();

        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);
        ClusterRoleBinding crb = kc.generateClusterRoleBinding(testNamespace);

        assertThat(crb.getMetadata().getName(), is(KafkaResources.initContainerClusterRoleBindingName(cluster, testNamespace)));
        assertThat(crb.getMetadata().getNamespace(), is(nullValue()));
        assertThat(crb.getSubjects().get(0).getNamespace(), is(testNamespace));
        assertThat(crb.getSubjects().get(0).getName(), is(kc.getServiceAccountName()));
    }

    @ParallelTest
    public void testClusterRoleBindingRack() {
        String testNamespace = "other-namespace";

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(testNamespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withNewRack("my-topology-label")
                    .endKafka()
                .endSpec()
                .build();

        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);
        ClusterRoleBinding crb = kc.generateClusterRoleBinding(testNamespace);

        assertThat(crb.getMetadata().getName(), is(KafkaResources.initContainerClusterRoleBindingName(cluster, testNamespace)));
        assertThat(crb.getMetadata().getNamespace(), is(nullValue()));
        assertThat(crb.getSubjects().get(0).getNamespace(), is(testNamespace));
        assertThat(crb.getSubjects().get(0).getName(), is(kc.getServiceAccountName()));
    }

    @ParallelTest
    public void testNullClusterRoleBinding() {
        String testNamespace = "other-namespace";

        Kafka kafkaAssembly = ResourceUtils.createKafka(testNamespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap());

        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);
        ClusterRoleBinding crb = kc.generateClusterRoleBinding(testNamespace);

        assertThat(crb, is(nullValue()));
    }

    @ParallelTest
    public void testKafkaContainerEnvars() {

        ContainerEnvVar envVar1 = new ContainerEnvVar();
        String testEnvOneKey = "TEST_ENV_1";
        String testEnvOneValue = "test.env.one";
        envVar1.setName(testEnvOneKey);
        envVar1.setValue(testEnvOneValue);

        ContainerEnvVar envVar2 = new ContainerEnvVar();
        String testEnvTwoKey = "TEST_ENV_2";
        String testEnvTwoValue = "test.env.two";
        envVar2.setName(testEnvTwoKey);
        envVar2.setValue(testEnvTwoValue);

        List<ContainerEnvVar> testEnvs = new ArrayList<>();
        testEnvs.add(envVar1);
        testEnvs.add(envVar2);

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withNewTemplate()
                            .withNewKafkaContainer()
                                .withEnv(testEnvs)
                            .endKafkaContainer()
                        .endTemplate()
                    .endKafka()
                .endSpec()
                .build();

        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        List<EnvVar> kafkaEnvVars = kc.getEnvVars();

        assertThat("Failed to correctly set container environment variable: " + testEnvOneKey,
                kafkaEnvVars.stream().filter(env -> testEnvOneKey.equals(env.getName()))
                        .map(EnvVar::getValue).findFirst().orElse("").equals(testEnvOneValue), is(true));
        assertThat("Failed to correctly set container environment variable: " + testEnvTwoKey,
                kafkaEnvVars.stream().filter(env -> testEnvTwoKey.equals(env.getName()))
                        .map(EnvVar::getValue).findFirst().orElse("").equals(testEnvTwoValue), is(true));

    }

    @ParallelTest
    public void testKafkaContainerEnvVarsConflict() {
        ContainerEnvVar envVar1 = new ContainerEnvVar();
        String testEnvOneKey = KafkaCluster.ENV_VAR_KAFKA_JMX_ENABLED;
        String testEnvOneValue = "test.env.one";
        envVar1.setName(testEnvOneKey);
        envVar1.setValue(testEnvOneValue);

        ContainerEnvVar envVar2 = new ContainerEnvVar();
        String testEnvTwoKey = KafkaCluster.ENV_VAR_STRIMZI_KAFKA_GC_LOG_ENABLED;
        String testEnvTwoValue = "test.env.two";
        envVar2.setName(testEnvTwoKey);
        envVar2.setValue(testEnvTwoValue);

        List<ContainerEnvVar> testEnvs = new ArrayList<>();
        testEnvs.add(envVar1);
        testEnvs.add(envVar2);
        ContainerTemplate kafkaContainer = new ContainerTemplate();
        kafkaContainer.setEnv(testEnvs);

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withNewTemplate()
                            .withKafkaContainer(kafkaContainer)
                        .endTemplate()
                        .withNewJmxOptions()
                            .withNewKafkaJmxAuthenticationPassword()
                            .endKafkaJmxAuthenticationPassword()
                        .endJmxOptions()
                    .endKafka()
                .endSpec()
                .build();

        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        List<EnvVar> kafkaEnvVars = kc.getEnvVars();

        assertThat("Failed to prevent over writing existing container environment variable: " + testEnvOneKey,
                kafkaEnvVars.stream().filter(env -> testEnvOneKey.equals(env.getName()))
                        .map(EnvVar::getValue).findFirst().orElse("").equals(testEnvOneValue), is(false));
        assertThat("Failed to prevent over writing existing container environment variable: " + testEnvTwoKey,
                kafkaEnvVars.stream().filter(env -> testEnvTwoKey.equals(env.getName()))
                        .map(EnvVar::getValue).findFirst().orElse("").equals(testEnvTwoValue), is(false));

    }

    @ParallelTest
    public void testInitContainerEnvVars() {

        ContainerEnvVar envVar1 = new ContainerEnvVar();
        String testEnvOneKey = "TEST_ENV_1";
        String testEnvOneValue = "test.env.one";
        envVar1.setName(testEnvOneKey);
        envVar1.setValue(testEnvOneValue);

        ContainerEnvVar envVar2 = new ContainerEnvVar();
        String testEnvTwoKey = "TEST_ENV_2";
        String testEnvTwoValue = "test.env.two";
        envVar2.setName(testEnvTwoKey);
        envVar2.setValue(testEnvTwoValue);

        List<ContainerEnvVar> testEnvs = new ArrayList<>();
        testEnvs.add(envVar1);
        testEnvs.add(envVar2);
        ContainerTemplate initContainer = new ContainerTemplate();
        initContainer.setEnv(testEnvs);

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                .editKafka()
                .withNewTemplate()
                .withInitContainer(initContainer)
                .endTemplate()
                .endKafka()
                .endSpec()
                .build();

        List<EnvVar> kafkaEnvVars = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS).getInitContainerEnvVars();

        assertThat("Failed to correctly set container environment variable: " + testEnvOneKey,
                kafkaEnvVars.stream().filter(env -> testEnvOneKey.equals(env.getName()))
                        .map(EnvVar::getValue).findFirst().orElse("").equals(testEnvOneValue), is(true));
        assertThat("Failed to correctly set container environment variable: " + testEnvTwoKey,
                kafkaEnvVars.stream().filter(env -> testEnvTwoKey.equals(env.getName()))
                        .map(EnvVar::getValue).findFirst().orElse("").equals(testEnvTwoValue), is(true));

    }

    @ParallelTest
    public void testInitContainerEnvVarsConflict() {
        ContainerEnvVar envVar1 = new ContainerEnvVar();
        String testEnvOneKey = KafkaCluster.ENV_VAR_KAFKA_INIT_EXTERNAL_ADDRESS;
        String testEnvOneValue = "test.env.one";
        envVar1.setName(testEnvOneKey);
        envVar1.setValue(testEnvOneValue);

        ContainerEnvVar envVar2 = new ContainerEnvVar();
        String testEnvTwoKey = KafkaCluster.ENV_VAR_KAFKA_INIT_EXTERNAL_ADDRESS;
        String testEnvTwoValue = "test.env.two";
        envVar2.setName(testEnvTwoKey);
        envVar2.setValue(testEnvTwoValue);

        List<ContainerEnvVar> testEnvs = new ArrayList<>();
        testEnvs.add(envVar1);
        testEnvs.add(envVar2);
        ContainerTemplate initContainer = new ContainerTemplate();
        initContainer.setEnv(testEnvs);

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withNewTemplate()
                            .withInitContainer(initContainer)
                        .endTemplate()
                        .withListeners(new GenericKafkaListenerBuilder()
                                .withName("external")
                                .withPort(9094)
                                .withType(KafkaListenerType.NODEPORT)
                                .withTls(true)
                                .withNewKafkaListenerAuthenticationTlsAuth()
                                .endKafkaListenerAuthenticationTlsAuth()
                                .build())
                    .endKafka()
                .endSpec()
                .build();

        List<EnvVar> kafkaEnvVars = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS).getInitContainerEnvVars();

        assertThat("Failed to prevent over writing existing container environment variable: " + testEnvOneKey,
                kafkaEnvVars.stream().filter(env -> testEnvOneKey.equals(env.getName()))
                        .map(EnvVar::getValue).findFirst().orElse("").equals(testEnvOneValue), is(false));
        assertThat("Failed to prevent over writing existing container environment variable: " + testEnvTwoKey,
                kafkaEnvVars.stream().filter(env -> testEnvTwoKey.equals(env.getName()))
                        .map(EnvVar::getValue).findFirst().orElse("").equals(testEnvTwoValue), is(false));

    }

    @ParallelTest
    public void testKafkaContainerSecurityContext() {

        SecurityContext securityContext = new SecurityContextBuilder()
                .withPrivileged(false)
                .withReadOnlyRootFilesystem(false)
                .withAllowPrivilegeEscalation(false)
                .withRunAsNonRoot(true)
                .withNewCapabilities()
                    .addToDrop("ALL")
                .endCapabilities()
                .build();

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withNewTemplate()
                            .withNewKafkaContainer()
                                .withSecurityContext(securityContext)
                            .endKafkaContainer()
                        .endTemplate()
                    .endKafka()
                .endSpec()
                .build();

        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);
        assertThat(kc.templateKafkaContainerSecurityContext, is(securityContext));

        StatefulSet sts = kc.generateStatefulSet(false, null, null, null);

        assertThat(sts.getSpec().getTemplate().getSpec().getContainers(),
                hasItem(allOf(
                        hasProperty("name", equalTo(KafkaCluster.KAFKA_NAME)),
                        hasProperty("securityContext", equalTo(securityContext))
                )));
    }

    @ParallelTest
    public void testInitContainerSecurityContext() {

        SecurityContext securityContext = new SecurityContextBuilder()
                .withPrivileged(false)
                .withReadOnlyRootFilesystem(false)
                .withAllowPrivilegeEscalation(false)
                .withRunAsNonRoot(true)
                .withNewCapabilities()
                    .addToDrop("ALL")
                .endCapabilities()
                .build();

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        // Set a rack to force init-container to be templated
                        .withNewRack()
                            .withTopologyKey("a-topology")
                        .endRack()
                        .withNewTemplate()
                            .withNewInitContainer()
                                .withSecurityContext(securityContext)
                            .endInitContainer()
                        .endTemplate()
                    .endKafka()
                .endSpec()
                .build();

        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);
        StatefulSet sts = kc.generateStatefulSet(false, null, null, null);

        assertThat(sts.getSpec().getTemplate().getSpec().getInitContainers(),
                hasItem(allOf(
                        hasProperty("name", equalTo(KafkaCluster.INIT_NAME)),
                        hasProperty("securityContext", equalTo(securityContext))
                )));
    }

    @ParallelTest
    public void testGenerateDeploymentWithOAuthWithClientSecret() {
        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withListeners(new GenericKafkaListenerBuilder()
                                .withName("plain")
                                .withPort(9092)
                                .withType(KafkaListenerType.INTERNAL)
                                .withTls(false)
                                .withAuth(
                                        new KafkaListenerAuthenticationOAuthBuilder()
                                                .withClientId("my-client-id")
                                                .withValidIssuerUri("http://valid-issuer")
                                                .withIntrospectionEndpointUri("http://introspection")
                                                .withNewClientSecret()
                                                .withSecretName("my-secret-secret")
                                                .withKey("my-secret-key")
                                                .endClientSecret()
                                                .build())
                                .build())
                    .endKafka()
                .endSpec()
                .build();

        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);
        StatefulSet sts = kc.generateStatefulSet(true, null, null, null);
        Container cont = sts.getSpec().getTemplate().getSpec().getContainers().get(0);

        assertThat(cont.getEnv().stream().filter(var -> "STRIMZI_PLAIN_9092_OAUTH_CLIENT_SECRET".equals(var.getName())).findFirst().orElseThrow().getValueFrom().getSecretKeyRef().getName(), is("my-secret-secret"));
        assertThat(cont.getEnv().stream().filter(var -> "STRIMZI_PLAIN_9092_OAUTH_CLIENT_SECRET".equals(var.getName())).findFirst().orElseThrow().getValueFrom().getSecretKeyRef().getKey(), is("my-secret-key"));
    }

    @ParallelTest
    public void testGenerateDeploymentWithOAuthWithClientSecretAndTls() {
        CertSecretSource cert1 = new CertSecretSourceBuilder()
                .withSecretName("first-certificate")
                .withCertificate("ca.crt")
                .build();

        CertSecretSource cert2 = new CertSecretSourceBuilder()
                .withSecretName("second-certificate")
                .withCertificate("tls.crt")
                .build();

        CertSecretSource cert3 = new CertSecretSourceBuilder()
                .withSecretName("first-certificate")
                .withCertificate("ca2.crt")
                .build();

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withListeners(new GenericKafkaListenerBuilder()
                                .withName("plain")
                                .withPort(9092)
                                .withType(KafkaListenerType.INTERNAL)
                                .withTls(false)
                                .withAuth(
                                        new KafkaListenerAuthenticationOAuthBuilder()
                                                .withClientId("my-client-id")
                                                .withValidIssuerUri("http://valid-issuer")
                                                .withIntrospectionEndpointUri("http://introspection")
                                                .withNewClientSecret()
                                                .withSecretName("my-secret-secret")
                                                .withKey("my-secret-key")
                                                .endClientSecret()
                                                .withDisableTlsHostnameVerification(true)
                                                .withTlsTrustedCertificates(cert1, cert2, cert3)
                                                .build())
                                .build())
                    .endKafka()
                .endSpec()
                .build();

        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);
        StatefulSet sts = kc.generateStatefulSet(true, null, null, null);
        Container cont = sts.getSpec().getTemplate().getSpec().getContainers().get(0);

        assertThat(cont.getEnv().stream().filter(var -> "STRIMZI_PLAIN_9092_OAUTH_CLIENT_SECRET".equals(var.getName())).findFirst().orElseThrow().getValueFrom().getSecretKeyRef().getName(), is("my-secret-secret"));
        assertThat(cont.getEnv().stream().filter(var -> "STRIMZI_PLAIN_9092_OAUTH_CLIENT_SECRET".equals(var.getName())).findFirst().orElseThrow().getValueFrom().getSecretKeyRef().getKey(), is("my-secret-key"));

        // Volume mounts
        assertThat(cont.getVolumeMounts().stream().filter(mount -> "oauth-plain-9092-0".equals(mount.getName())).findFirst().orElseThrow().getMountPath(), is(KafkaCluster.OAUTH_TRUSTED_CERTS_BASE_VOLUME_MOUNT + "/oauth-plain-9092-certs/first-certificate-0"));
        assertThat(cont.getVolumeMounts().stream().filter(mount -> "oauth-plain-9092-1".equals(mount.getName())).findFirst().orElseThrow().getMountPath(), is(KafkaCluster.OAUTH_TRUSTED_CERTS_BASE_VOLUME_MOUNT + "/oauth-plain-9092-certs/second-certificate-1"));
        assertThat(cont.getVolumeMounts().stream().filter(mount -> "oauth-plain-9092-2".equals(mount.getName())).findFirst().orElseThrow().getMountPath(), is(KafkaCluster.OAUTH_TRUSTED_CERTS_BASE_VOLUME_MOUNT + "/oauth-plain-9092-certs/first-certificate-2"));

        // Volumes
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-plain-9092-0".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().size(), is(1));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-plain-9092-0".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().get(0).getKey(), is("ca.crt"));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-plain-9092-0".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().get(0).getPath(), is("tls.crt"));

        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-plain-9092-1".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().size(), is(1));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-plain-9092-1".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().get(0).getKey(), is("tls.crt"));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-plain-9092-1".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().get(0).getPath(), is("tls.crt"));

        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-plain-9092-2".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().size(), is(1));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-plain-9092-2".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().get(0).getKey(), is("ca2.crt"));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-plain-9092-2".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().get(0).getPath(), is("tls.crt"));
    }

    @ParallelTest
    public void testGenerateDeploymentWithOAuthEverywhere() {
        CertSecretSource cert1 = new CertSecretSourceBuilder()
                .withSecretName("first-certificate")
                .withCertificate("ca.crt")
                .build();

        CertSecretSource cert2 = new CertSecretSourceBuilder()
                .withSecretName("second-certificate")
                .withCertificate("tls.crt")
                .build();

        CertSecretSource cert3 = new CertSecretSourceBuilder()
                .withSecretName("first-certificate")
                .withCertificate("ca2.crt")
                .build();

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withListeners(new GenericKafkaListenerBuilder()
                                    .withName("plain")
                                    .withPort(9092)
                                    .withType(KafkaListenerType.INTERNAL)
                                    .withTls(false)
                                    .withAuth(
                                            new KafkaListenerAuthenticationOAuthBuilder()
                                                    .withClientId("my-client-id")
                                                    .withValidIssuerUri("http://valid-issuer")
                                                    .withIntrospectionEndpointUri("http://introspection")
                                                    .withNewClientSecret()
                                                    .withSecretName("my-secret-secret")
                                                    .withKey("my-secret-key")
                                                    .endClientSecret()
                                                    .withDisableTlsHostnameVerification(true)
                                                    .withTlsTrustedCertificates(cert1, cert2, cert3)
                                                    .build())
                                    .build(),
                                new GenericKafkaListenerBuilder()
                                    .withName("tls")
                                    .withPort(9093)
                                    .withType(KafkaListenerType.INTERNAL)
                                    .withTls(true)
                                    .withAuth(
                                            new KafkaListenerAuthenticationOAuthBuilder()
                                                    .withClientId("my-client-id")
                                                    .withValidIssuerUri("http://valid-issuer")
                                                    .withIntrospectionEndpointUri("http://introspection")
                                                    .withNewClientSecret()
                                                    .withSecretName("my-secret-secret")
                                                    .withKey("my-secret-key")
                                                    .endClientSecret()
                                                    .withDisableTlsHostnameVerification(true)
                                                    .withTlsTrustedCertificates(cert1, cert2, cert3)
                                                    .build())
                                    .build(),
                                new GenericKafkaListenerBuilder()
                                    .withName("external")
                                    .withPort(9094)
                                    .withType(KafkaListenerType.NODEPORT)
                                    .withTls(true)
                                    .withAuth(
                                            new KafkaListenerAuthenticationOAuthBuilder()
                                                    .withClientId("my-client-id")
                                                    .withValidIssuerUri("http://valid-issuer")
                                                    .withIntrospectionEndpointUri("http://introspection")
                                                    .withNewClientSecret()
                                                    .withSecretName("my-secret-secret")
                                                    .withKey("my-secret-key")
                                                    .endClientSecret()
                                                    .withDisableTlsHostnameVerification(true)
                                                    .withTlsTrustedCertificates(cert1, cert2, cert3)
                                                    .build())
                                    .build())
                    .endKafka()
                .endSpec()
                .build();

        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);
        StatefulSet sts = kc.generateStatefulSet(true, null, null, null);
        Container cont = sts.getSpec().getTemplate().getSpec().getContainers().get(0);

        assertThat(cont.getEnv().stream().filter(var -> "STRIMZI_PLAIN_9092_OAUTH_CLIENT_SECRET".equals(var.getName())).findFirst().orElseThrow().getValueFrom().getSecretKeyRef().getName(), is("my-secret-secret"));
        assertThat(cont.getEnv().stream().filter(var -> "STRIMZI_PLAIN_9092_OAUTH_CLIENT_SECRET".equals(var.getName())).findFirst().orElseThrow().getValueFrom().getSecretKeyRef().getKey(), is("my-secret-key"));

        assertThat(cont.getEnv().stream().filter(var -> "STRIMZI_TLS_9093_OAUTH_CLIENT_SECRET".equals(var.getName())).findFirst().orElseThrow().getValueFrom().getSecretKeyRef().getName(), is("my-secret-secret"));
        assertThat(cont.getEnv().stream().filter(var -> "STRIMZI_TLS_9093_OAUTH_CLIENT_SECRET".equals(var.getName())).findFirst().orElseThrow().getValueFrom().getSecretKeyRef().getKey(), is("my-secret-key"));

        assertThat(cont.getEnv().stream().filter(var -> "STRIMZI_EXTERNAL_9094_OAUTH_CLIENT_SECRET".equals(var.getName())).findFirst().orElseThrow().getValueFrom().getSecretKeyRef().getName(), is("my-secret-secret"));
        assertThat(cont.getEnv().stream().filter(var -> "STRIMZI_EXTERNAL_9094_OAUTH_CLIENT_SECRET".equals(var.getName())).findFirst().orElseThrow().getValueFrom().getSecretKeyRef().getKey(), is("my-secret-key"));

        // Volume mounts
        assertThat(cont.getVolumeMounts().stream().filter(mount -> "oauth-plain-9092-0".equals(mount.getName())).findFirst().orElseThrow().getMountPath(), is(KafkaCluster.OAUTH_TRUSTED_CERTS_BASE_VOLUME_MOUNT + "/oauth-plain-9092-certs/first-certificate-0"));
        assertThat(cont.getVolumeMounts().stream().filter(mount -> "oauth-plain-9092-1".equals(mount.getName())).findFirst().orElseThrow().getMountPath(), is(KafkaCluster.OAUTH_TRUSTED_CERTS_BASE_VOLUME_MOUNT + "/oauth-plain-9092-certs/second-certificate-1"));
        assertThat(cont.getVolumeMounts().stream().filter(mount -> "oauth-plain-9092-2".equals(mount.getName())).findFirst().orElseThrow().getMountPath(), is(KafkaCluster.OAUTH_TRUSTED_CERTS_BASE_VOLUME_MOUNT + "/oauth-plain-9092-certs/first-certificate-2"));

        assertThat(cont.getVolumeMounts().stream().filter(mount -> "oauth-tls-9093-0".equals(mount.getName())).findFirst().orElseThrow().getMountPath(), is(KafkaCluster.OAUTH_TRUSTED_CERTS_BASE_VOLUME_MOUNT + "/oauth-tls-9093-certs/first-certificate-0"));
        assertThat(cont.getVolumeMounts().stream().filter(mount -> "oauth-tls-9093-1".equals(mount.getName())).findFirst().orElseThrow().getMountPath(), is(KafkaCluster.OAUTH_TRUSTED_CERTS_BASE_VOLUME_MOUNT + "/oauth-tls-9093-certs/second-certificate-1"));
        assertThat(cont.getVolumeMounts().stream().filter(mount -> "oauth-tls-9093-2".equals(mount.getName())).findFirst().orElseThrow().getMountPath(), is(KafkaCluster.OAUTH_TRUSTED_CERTS_BASE_VOLUME_MOUNT + "/oauth-tls-9093-certs/first-certificate-2"));

        assertThat(cont.getVolumeMounts().stream().filter(mount -> "oauth-external-9094-0".equals(mount.getName())).findFirst().orElseThrow().getMountPath(), is(KafkaCluster.OAUTH_TRUSTED_CERTS_BASE_VOLUME_MOUNT + "/oauth-external-9094-certs/first-certificate-0"));
        assertThat(cont.getVolumeMounts().stream().filter(mount -> "oauth-external-9094-1".equals(mount.getName())).findFirst().orElseThrow().getMountPath(), is(KafkaCluster.OAUTH_TRUSTED_CERTS_BASE_VOLUME_MOUNT + "/oauth-external-9094-certs/second-certificate-1"));
        assertThat(cont.getVolumeMounts().stream().filter(mount -> "oauth-external-9094-2".equals(mount.getName())).findFirst().orElseThrow().getMountPath(), is(KafkaCluster.OAUTH_TRUSTED_CERTS_BASE_VOLUME_MOUNT + "/oauth-external-9094-certs/first-certificate-2"));

        // Volumes
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-plain-9092-0".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().size(), is(1));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-plain-9092-0".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().get(0).getKey(), is("ca.crt"));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-plain-9092-0".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().get(0).getPath(), is("tls.crt"));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-plain-9092-1".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().size(), is(1));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-plain-9092-1".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().get(0).getKey(), is("tls.crt"));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-plain-9092-1".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().get(0).getPath(), is("tls.crt"));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-plain-9092-2".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().size(), is(1));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-plain-9092-2".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().get(0).getKey(), is("ca2.crt"));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-plain-9092-2".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().get(0).getPath(), is("tls.crt"));

        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-tls-9093-0".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().size(), is(1));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-tls-9093-0".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().get(0).getKey(), is("ca.crt"));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-tls-9093-0".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().get(0).getPath(), is("tls.crt"));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-tls-9093-1".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().size(), is(1));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-tls-9093-1".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().get(0).getKey(), is("tls.crt"));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-tls-9093-1".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().get(0).getPath(), is("tls.crt"));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-tls-9093-2".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().size(), is(1));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-tls-9093-2".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().get(0).getKey(), is("ca2.crt"));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-tls-9093-2".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().get(0).getPath(), is("tls.crt"));

        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-external-9094-0".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().size(), is(1));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-external-9094-0".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().get(0).getKey(), is("ca.crt"));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-external-9094-0".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().get(0).getPath(), is("tls.crt"));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-external-9094-1".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().size(), is(1));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-external-9094-1".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().get(0).getKey(), is("tls.crt"));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-external-9094-1".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().get(0).getPath(), is("tls.crt"));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-external-9094-2".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().size(), is(1));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-external-9094-2".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().get(0).getKey(), is("ca2.crt"));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "oauth-external-9094-2".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().get(0).getPath(), is("tls.crt"));
    }

    @ParallelTest
    public void testCustomAuthSecretsAreMounted() {
        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                .editKafka()
                .withListeners(new GenericKafkaListenerBuilder()
                        .withName("plain")
                        .withPort(9092)
                        .withType(KafkaListenerType.INTERNAL)
                        .withTls(false)
                        .withAuth(
                                new KafkaListenerAuthenticationCustomBuilder()
                                        .withSecrets(new GenericSecretSourceBuilder().withSecretName("test").withKey("foo").build(),
                                                new GenericSecretSourceBuilder().withSecretName("test2").withKey("bar").build())
                                        .build())
                        .build())
                .endKafka()
                .endSpec()
                .build();

        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);
        StatefulSet sts = kc.generateStatefulSet(true, null, null, null);
        Container cont = sts.getSpec().getTemplate().getSpec().getContainers().get(0);

        // Volume mounts
        assertThat(cont.getVolumeMounts().stream().filter(mount -> "custom-listener-plain-9092-0".equals(mount.getName())).findFirst().orElseThrow().getMountPath(), is(KafkaCluster.CUSTOM_AUTHN_SECRETS_VOLUME_MOUNT + "/custom-listener-plain-9092/test"));
        assertThat(cont.getVolumeMounts().stream().filter(mount -> "custom-listener-plain-9092-1".equals(mount.getName())).findFirst().orElseThrow().getMountPath(), is(KafkaCluster.CUSTOM_AUTHN_SECRETS_VOLUME_MOUNT + "/custom-listener-plain-9092/test2"));

        // Volumes
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "custom-listener-plain-9092-0".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().size(), is(1));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "custom-listener-plain-9092-0".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().get(0).getKey(), is("foo"));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "custom-listener-plain-9092-0".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().get(0).getPath(), is("foo"));

        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "custom-listener-plain-9092-1".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().size(), is(1));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "custom-listener-plain-9092-1".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().get(0).getKey(), is("bar"));
        assertThat(sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(vol -> "custom-listener-plain-9092-1".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().get(0).getPath(), is("bar"));
    }

    @ParallelTest
    public void testExternalCertificateIngress() {
        String cert = "my-external-cert.crt";
        String key = "my.key";
        String secret = "my-secret";

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withListeners(new GenericKafkaListenerBuilder()
                                .withName("external")
                                .withPort(9094)
                                .withType(KafkaListenerType.NODEPORT)
                                .withTls(true)
                                .withNewConfiguration()
                                    .withNewBrokerCertChainAndKey()
                                        .withCertificate(cert)
                                        .withKey(key)
                                        .withSecretName(secret)
                                    .endBrokerCertChainAndKey()
                                .endConfiguration()
                                .build())
                    .endKafka()
                .endSpec()
                .build();

        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        StatefulSet sts = kc.generateStatefulSet(true, null, null, null);

        Volume vol = sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(v -> "custom-external-9094-certs".equals(v.getName())).findFirst().orElse(null);

        assertThat(vol, is(notNullValue()));
        assertThat(vol.getSecret().getSecretName(), is(secret));
        assertThat(vol.getSecret().getItems().get(0).getKey(), is(key));
        assertThat(vol.getSecret().getItems().get(0).getPath(), is("tls.key"));
        assertThat(vol.getSecret().getItems().get(1).getKey(), is(cert));
        assertThat(vol.getSecret().getItems().get(1).getPath(), is("tls.crt"));

        VolumeMount mount = sts.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts().stream().filter(v -> "custom-external-9094-certs".equals(v.getName())).findFirst().orElse(null);

        assertThat(mount, is(notNullValue()));
        assertThat(mount.getName(), is("custom-external-9094-certs"));
        assertThat(mount.getMountPath(), is("/opt/kafka/certificates/custom-external-9094-certs"));
    }

    @ParallelTest
    public void testCustomCertificateTls() {
        String cert = "my-external-cert.crt";
        String key = "my.key";
        String secret = "my-secret";

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withListeners(new GenericKafkaListenerBuilder()
                                .withName("tls")
                                .withPort(9093)
                                .withType(KafkaListenerType.INTERNAL)
                                .withTls(true)
                                .withNewConfiguration()
                                    .withNewBrokerCertChainAndKey()
                                        .withCertificate(cert)
                                        .withKey(key)
                                        .withSecretName(secret)
                                    .endBrokerCertChainAndKey()
                                .endConfiguration()
                                .build())
                    .endKafka()
                .endSpec()
                .build();

        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);
        StatefulSet sts = kc.generateStatefulSet(true, null, null, null);

        Volume vol = sts.getSpec().getTemplate().getSpec().getVolumes().stream().filter(v -> "custom-tls-9093-certs".equals(v.getName())).findFirst().orElse(null);

        assertThat(vol, is(notNullValue()));
        assertThat(vol.getSecret().getSecretName(), is(secret));
        assertThat(vol.getSecret().getItems().get(0).getKey(), is(key));
        assertThat(vol.getSecret().getItems().get(0).getPath(), is("tls.key"));
        assertThat(vol.getSecret().getItems().get(1).getKey(), is(cert));
        assertThat(vol.getSecret().getItems().get(1).getPath(), is("tls.crt"));

        VolumeMount mount = sts.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts().stream().filter(v -> "custom-tls-9093-certs".equals(v.getName())).findFirst().orElse(null);

        assertThat(mount, is(notNullValue()));
        assertThat(mount.getName(), is("custom-tls-9093-certs"));
        assertThat(mount.getMountPath(), is("/opt/kafka/certificates/custom-tls-9093-certs"));
    }

    @ParallelTest
    public void testGenerateDeploymentWithKeycloakAuthorizationMissingOAuthListeners() {
        assertThrows(InvalidResourceException.class, () -> {
            Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                    image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                    .editSpec()
                    .editKafka()
                    .withAuthorization(
                            new KafkaAuthorizationKeycloakBuilder()
                                    .build())
                    .endKafka()
                    .endSpec()
                    .build();

            KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);
        });
    }

    @ParallelTest
    public void testGenerateDeploymentWithKeycloakAuthorization() {
        CertSecretSource cert1 = new CertSecretSourceBuilder()
                .withSecretName("first-certificate")
                .withCertificate("ca.crt")
                .build();

        CertSecretSource cert2 = new CertSecretSourceBuilder()
                .withSecretName("second-certificate")
                .withCertificate("tls.crt")
                .build();

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withListeners(new GenericKafkaListenerBuilder()
                                .withName("plain")
                                .withPort(9092)
                                .withType(KafkaListenerType.INTERNAL)
                                .withTls(false)
                                .withAuth(
                                        new KafkaListenerAuthenticationOAuthBuilder()
                                                .withClientId("my-client-id")
                                                .withValidIssuerUri("http://valid-issuer")
                                                .withIntrospectionEndpointUri("http://introspection")
                                                .withMaxSecondsWithoutReauthentication(3600)
                                                .withNewClientSecret()
                                                .withSecretName("my-secret-secret")
                                                .withKey("my-secret-key")
                                                .endClientSecret()
                                                .withDisableTlsHostnameVerification(true)
                                                .withTlsTrustedCertificates(cert1, cert2)
                                                .build())
                                .build())
                    .withAuthorization(
                            new KafkaAuthorizationKeycloakBuilder()
                                    .withClientId("my-client-id")
                                    .withTokenEndpointUri("http://token-endpoint-uri")
                                    .withDisableTlsHostnameVerification(true)
                                    .withDelegateToKafkaAcls(false)
                                    .withGrantsRefreshPeriodSeconds(90)
                                    .withGrantsRefreshPoolSize(4)
                                    .withTlsTrustedCertificates(cert1, cert2)
                                    .build())
                    .endKafka()
                .endSpec()
                .build();

        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);
        StatefulSet sts = kc.generateStatefulSet(true, null, null, null);
        Container cont = sts.getSpec().getTemplate().getSpec().getContainers().get(0);

        // Volume mounts
        assertThat(cont.getVolumeMounts().stream().filter(mount -> "authz-keycloak-0".equals(mount.getName())).findFirst().orElseThrow().getMountPath(), is(KafkaCluster.OAUTH_TRUSTED_CERTS_BASE_VOLUME_MOUNT + "/authz-keycloak-certs/first-certificate-0"));
        assertThat(cont.getVolumeMounts().stream().filter(mount -> "authz-keycloak-1".equals(mount.getName())).findFirst().orElseThrow().getMountPath(), is(KafkaCluster.OAUTH_TRUSTED_CERTS_BASE_VOLUME_MOUNT + "/authz-keycloak-certs/second-certificate-1"));

        // Volumes
        List<Volume> volumes = sts.getSpec().getTemplate().getSpec().getVolumes();
        assertThat(volumes.stream().filter(vol -> "authz-keycloak-0".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().size(), is(1));
        assertThat(volumes.stream().filter(vol -> "authz-keycloak-0".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().get(0).getKey(), is("ca.crt"));
        assertThat(volumes.stream().filter(vol -> "authz-keycloak-0".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().get(0).getPath(), is("tls.crt"));
        assertThat(volumes.stream().filter(vol -> "authz-keycloak-1".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().size(), is(1));
        assertThat(volumes.stream().filter(vol -> "authz-keycloak-1".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().get(0).getKey(), is("tls.crt"));
        assertThat(volumes.stream().filter(vol -> "authz-keycloak-1".equals(vol.getName())).findFirst().orElseThrow().getSecret().getItems().get(0).getPath(), is("tls.crt"));
    }

    @ParallelTest
    public void testReplicasAndRelatedOptionsValidationNok() {
        String propertyName = "offsets.topic.replication.factor";
        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withConfig(singletonMap(propertyName, replicas + 1))
                    .endKafka()
                .endSpec()
                .build();
        InvalidResourceException ex = assertThrows(InvalidResourceException.class, () -> KafkaCluster.validateIntConfigProperty(propertyName, kafkaAssembly.getSpec().getKafka()));
        assertThat(ex.getMessage().equals("Kafka configuration option '" + propertyName + "' should be set to " + replicas + " or less because 'spec.kafka.replicas' is " + replicas), is(true));
    }

    @ParallelTest
    public void testReplicasAndRelatedOptionsValidationOk() {

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withConfig(singletonMap("offsets.topic.replication.factor", replicas - 1))
                    .endKafka()
                .endSpec()
                .build();
        KafkaCluster.validateIntConfigProperty("offsets.topic.replication.factor", kafkaAssembly.getSpec().getKafka());
    }

    @ParallelTest
    public void testCruiseControl() {

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                .withNewCruiseControl()
                .endCruiseControl()
                .endSpec()
                .build();
        KafkaCluster kafkaCluster = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);
        String brokerConfig = kafkaCluster.generateSharedBrokerConfiguration(true);
        assertThat(brokerConfig, CoreMatchers.containsString(CruiseControlConfigurationParameters.METRICS_TOPIC_NUM_PARTITIONS + "=" + 1));
        assertThat(brokerConfig, CoreMatchers.containsString(CruiseControlConfigurationParameters.METRICS_TOPIC_REPLICATION_FACTOR + "=" + 1));
        assertThat(brokerConfig, CoreMatchers.containsString(CruiseControlConfigurationParameters.METRICS_TOPIC_MIN_ISR + "=" + 1));
    }

    @ParallelTest
    public void testCruiseControlCustomMetricsReporterTopic() {
        int replicationFactor = 3;
        int minInsync = 2;
        int partitions = 5;
        Map<String, Object> config = new HashMap<>();
        config.put(CruiseControlConfigurationParameters.METRICS_TOPIC_NUM_PARTITIONS.getValue(), partitions);
        config.put(CruiseControlConfigurationParameters.METRICS_TOPIC_REPLICATION_FACTOR.getValue(), replicationFactor);
        config.put(CruiseControlConfigurationParameters.METRICS_TOPIC_MIN_ISR.getValue(), minInsync);

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                .editKafka()
                .withConfig(config)
                .endKafka()
                .withNewCruiseControl()
                .endCruiseControl()
                .endSpec()
                .build();
        KafkaCluster kafkaCluster = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);
        String brokerConfig = kafkaCluster.generateSharedBrokerConfiguration(true);
        assertThat(brokerConfig, CoreMatchers.containsString(CruiseControlConfigurationParameters.METRICS_TOPIC_NUM_PARTITIONS + "=" + partitions));
        assertThat(brokerConfig, CoreMatchers.containsString(CruiseControlConfigurationParameters.METRICS_TOPIC_REPLICATION_FACTOR + "=" + replicationFactor));
        assertThat(brokerConfig, CoreMatchers.containsString(CruiseControlConfigurationParameters.METRICS_TOPIC_MIN_ISR + "=" + minInsync));
    }

    @ParallelTest
    public void testCruiseControlCustomMetricsReporterTopicMinInsync() {
        int minInsync = 1;
        Map<String, Object> config = new HashMap<>();
        config.put(CruiseControlConfigurationParameters.METRICS_TOPIC_MIN_ISR.getValue(), minInsync);

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                .editKafka()
                .withConfig(config)
                .endKafka()
                .withNewCruiseControl()
                .endCruiseControl()
                .endSpec()
                .build();
        KafkaCluster kafkaCluster = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);
        String brokerConfig = kafkaCluster.generateSharedBrokerConfiguration(true);
        assertThat(brokerConfig, CoreMatchers.containsString(CruiseControlConfigurationParameters.METRICS_TOPIC_MIN_ISR + "=" + minInsync));
    }

    @ParallelTest
    public void testCruiseControlWithSingleNodeKafka() {
        Map<String, Object> config = new HashMap<>();
        config.put("offsets.topic.replication.factor", 1);
        config.put("transaction.state.log.replication.factor", 1);
        config.put("transaction.state.log.min.isr", 1);

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withReplicas(1)
                        .withConfig(config)
                    .endKafka()
                    .withNewCruiseControl()
                    .endCruiseControl()
                .endSpec()
                .build();
        InvalidResourceException ex = assertThrows(InvalidResourceException.class, () -> KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS));
        assertThat(ex.getMessage(), is("Kafka " + namespace + "/" + cluster + " has invalid configuration. " +
                "Cruise Control cannot be deployed with a single-node Kafka cluster. It requires at least two Kafka nodes."));
    }

    @ParallelTest
    public void testCruiseControlWithMinISRgtReplicas() {
        Map<String, Object> config = new HashMap<>();
        int minInsyncReplicas = 3;
        config.put(CruiseControlConfigurationParameters.METRICS_TOPIC_REPLICATION_FACTOR.getValue(), 2);
        config.put(CruiseControlConfigurationParameters.METRICS_TOPIC_MIN_ISR.getValue(), minInsyncReplicas);

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withConfig(config)
                    .endKafka()
                .withNewCruiseControl()
                .endCruiseControl()
                .endSpec()
                .build();

        assertThrows(IllegalArgumentException.class, () -> KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS));
    }

    @ParallelTest
    public void testCruiseControlWithMinISRgtDefaultReplicas() {
        Map<String, Object> config = new HashMap<>();
        int minInsyncReplicas = 2;
        config.put(CruiseControlConfigurationParameters.METRICS_TOPIC_MIN_ISR.getValue(), minInsyncReplicas);

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                .editKafka()
                .withConfig(config)
                .endKafka()
                .withNewCruiseControl()
                .endCruiseControl()
                .endSpec()
                .build();

        assertThrows(IllegalArgumentException.class, () -> KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS));
    }

    @ParallelTest
    public void testMetricsParsingFromConfigMap() {
        MetricsConfig metrics = new JmxPrometheusExporterMetricsBuilder()
                .withNewValueFrom()
                    .withConfigMapKeyRef(new ConfigMapKeySelectorBuilder().withName("my-metrics-configuration").withKey("config.yaml").build())
                .endValueFrom()
                .build();

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout))
                .editSpec()
                    .editKafka()
                        .withMetricsConfig(metrics)
                    .endKafka()
                .endSpec()
                .build();

        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);

        assertThat(kc.isMetricsEnabled(), is(true));
        assertThat(kc.getMetricsConfigInCm(), is(metrics));
    }

    @ParallelTest
    public void testMetricsParsingNoMetrics() {
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout), VERSIONS);

        assertThat(kc.isMetricsEnabled(), is(false));
        assertThat(kc.getMetricsConfigInCm(), is(nullValue()));
    }

    @ParallelTest
    public void testKafkaInitContainerSectionIsConfigurable() {
        Map<String, Quantity> limits = new HashMap<>();
        limits.put("cpu", Quantity.parse("1"));
        limits.put("memory", Quantity.parse("256Mi"));

        Map<String, Quantity> requirements = new HashMap<>();
        requirements.put("cpu", Quantity.parse("100m"));
        requirements.put("memory", Quantity.parse("128Mi"));

        ResourceRequirements resourceReq = new ResourceRequirementsBuilder()
            .withLimits(limits)
            .withRequests(requirements)
            .build();

        Kafka kafka = new KafkaBuilder(kafkaAssembly)
            .editSpec()
                .editKafka()
                    .withResources(resourceReq)
                    .withNewRack()
                        .withTopologyKey("rack-key")
                    .endRack()
                .endKafka()
            .endSpec()
            .build();

        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafka, VERSIONS);

        ResourceRequirements initContainersResources = kc.getInitContainers(ImagePullPolicy.IFNOTPRESENT).get(0).getResources();
        assertThat(initContainersResources.getRequests(), is(requirements));
        assertThat(initContainersResources.getLimits(), is(limits));
    }

    @ParallelTest
    public void testInvalidVersion() {
        Kafka kafka = new KafkaBuilder(kafkaAssembly)
                .editSpec()
                    .editKafka()
                        .withVersion("6.6.6")
                    .endKafka()
                .endSpec()
                .build();

        InvalidResourceException exc = assertThrows(KafkaVersion.UnsupportedKafkaVersionException.class, () -> KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafka, VERSIONS));

        assertThat(exc.getMessage(), containsString("Unsupported Kafka.spec.kafka.version: 6.6.6. Supported versions are:"));
    }

    @ParallelTest
    public void testUnsupportedVersion() {
        Kafka kafka = new KafkaBuilder(kafkaAssembly)
                .editSpec()
                    .editKafka()
                        .withVersion("2.6.0")
                    .endKafka()
                .endSpec()
                .build();

        InvalidResourceException exc = assertThrows(KafkaVersion.UnsupportedKafkaVersionException.class, () -> KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafka, VERSIONS));

        assertThat(exc.getMessage(), containsString("Unsupported Kafka.spec.kafka.version: 2.6.0. Supported versions are:"));
    }

    @ParallelTest
    public void testInvalidVersionWithCustomImage() {
        Kafka kafka = new KafkaBuilder(kafkaAssembly)
                .editSpec()
                    .editKafka()
                        .withVersion("2.6.0")
                        .withImage("my-custom/image:latest")
                    .endKafka()
                .endSpec()
                .build();

        InvalidResourceException exc = assertThrows(KafkaVersion.UnsupportedKafkaVersionException.class, () -> KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafka, VERSIONS));

        assertThat(exc.getMessage(), containsString("Unsupported Kafka.spec.kafka.version: 2.6.0. Supported versions are:"));
    }

    @ParallelTest
    public void testNewKraftCluster() {
        Map<Integer, Map<String, String>> advertisedHostnames = Map.of(
                0, Map.of("PLAIN_9092", "broker-0", "TLS_9093", "broker-0"),
                1, Map.of("PLAIN_9092", "broker-1", "TLS_9093", "broker-1"),
                2, Map.of("PLAIN_9092", "broker-2", "TLS_9093", "broker-2")
        );
        Map<Integer, Map<String, String>> advertisedPorts = Map.of(
                0, Map.of("PLAIN_9092", "9092", "TLS_9093", "9093"),
                1, Map.of("PLAIN_9092", "9092", "TLS_9093", "9093"),
                2, Map.of("PLAIN_9092", "9092", "TLS_9093", "9093")
        );

        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS, null, 0, true);

        // Test that the broker configuration is with KRaft
        String config = kc.generatePerBrokerBrokerConfiguration(2, advertisedHostnames, advertisedPorts, true);
        assertThat(config, CoreMatchers.containsString("process.roles"));
        assertThat(config, CoreMatchers.containsString("controller.quorum.voters"));

        // Test that ClusterID KRaft flag are passed as environment variable
        List<EnvVar> kafkaEnvVars = kc.getEnvVars();
        EnvVar clusterIdEnvVar = kafkaEnvVars.stream().filter(env -> KafkaCluster.ENV_VAR_STRIMZI_CLUSTER_ID.equals(env.getName())).findFirst().orElse(null);
        assertThat(clusterIdEnvVar, is(Matchers.notNullValue()));
        assertThat(clusterIdEnvVar.getValue().isEmpty(), is(false));
        EnvVar kraftEnabledEnvVar = kafkaEnvVars.stream().filter(env -> KafkaCluster.ENV_VAR_STRIMZI_KRAFT_ENABLED.equals(env.getName())).findFirst().orElse(null);
        assertThat(kraftEnabledEnvVar, is(Matchers.notNullValue()));
        assertThat(kraftEnabledEnvVar.getValue().isEmpty(), is(false));
    }

    @ParallelTest
    public void testExistingKraftCluster() {
        Map<Integer, Map<String, String>> advertisedHostnames = Map.of(
                0, Map.of("PLAIN_9092", "broker-0", "TLS_9093", "broker-0"),
                1, Map.of("PLAIN_9092", "broker-1", "TLS_9093", "broker-1"),
                2, Map.of("PLAIN_9092", "broker-2", "TLS_9093", "broker-2")
        );
        Map<Integer, Map<String, String>> advertisedPorts = Map.of(
                0, Map.of("PLAIN_9092", "9092", "TLS_9093", "9093"),
                1, Map.of("PLAIN_9092", "9092", "TLS_9093", "9093"),
                2, Map.of("PLAIN_9092", "9092", "TLS_9093", "9093")
        );

        String clusterId = Uuid.randomUuid().toString();

        Kafka existingKafka = new KafkaBuilder(kafkaAssembly)
                .withNewStatus()
                    .withClusterId(clusterId)
                .endStatus()
                .build();

        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, existingKafka, VERSIONS, null, 0, true);

        // Test that the broker configuration is with KRaft
        String config = kc.generatePerBrokerBrokerConfiguration(2, advertisedHostnames, advertisedPorts, true);
        assertThat(config, CoreMatchers.containsString("process.roles"));
        assertThat(config, CoreMatchers.containsString("controller.quorum.voters"));

        // Test that ClusterID and KRaft flag are passed as environment variable
        List<EnvVar> kafkaEnvVars = kc.getEnvVars();
        EnvVar clusterIdEnvVar = kafkaEnvVars.stream().filter(env -> KafkaCluster.ENV_VAR_STRIMZI_CLUSTER_ID.equals(env.getName())).findFirst().orElse(null);
        assertThat(clusterIdEnvVar, is(Matchers.notNullValue()));
        assertThat(clusterIdEnvVar.getValue(), is(clusterId));
        EnvVar kraftEnabledEnvVar = kafkaEnvVars.stream().filter(env -> KafkaCluster.ENV_VAR_STRIMZI_KRAFT_ENABLED.equals(env.getName())).findFirst().orElse(null);
        assertThat(kraftEnabledEnvVar, is(Matchers.notNullValue()));
        assertThat(kraftEnabledEnvVar.getValue().isEmpty(), is(false));
    }

    @ParallelTest
    public void testRestrictedSecurityContextWithTemplate() {
        PodSecurityContext podSecurityContext = new PodSecurityContextBuilder()
                .withFsGroup(123L)
                .withRunAsGroup(456L)
                .withRunAsUser(789L)
                .build();

        SecurityContext securityContext = new SecurityContextBuilder()
                .withPrivileged(false)
                .withReadOnlyRootFilesystem(false)
                .withAllowPrivilegeEscalation(false)
                .withRunAsNonRoot(true)
                .withNewCapabilities()
                    .addToDrop("ALL")
                .endCapabilities()
                .build();

        Kafka kafkaAssembly = new KafkaBuilder(ResourceUtils.createKafka(namespace, cluster, replicas,
                image, healthDelay, healthTimeout, jmxMetricsConfig, configuration, emptyMap()))
                .editSpec()
                    .editKafka()
                        .withNewTemplate()
                            .withNewKafkaContainer()
                                .withSecurityContext(securityContext)
                            .endKafkaContainer()
                            .withNewPod()
                                .withSecurityContext(podSecurityContext)
                            .endPod()
                        .endTemplate()
                    .endKafka()
                .endSpec()
                .build();

        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);
        kc.securityProvider = new RestrictedPodSecurityProvider();
        kc.securityProvider.configure(new PlatformFeaturesAvailability(false, KubernetesVersion.MINIMAL_SUPPORTED_VERSION));

        StatefulSet sts = kc.generateStatefulSet(false, null, null, null);

        assertThat(sts.getSpec().getTemplate().getSpec().getSecurityContext(), is(podSecurityContext));

        assertThat(sts.getSpec().getTemplate().getSpec().getContainers().get(0).getSecurityContext(), is(securityContext));
    }

    @ParallelTest
    public void testRestrictedSecurityContext() {
        KafkaCluster kc = KafkaCluster.fromCrd(Reconciliation.DUMMY_RECONCILIATION, kafkaAssembly, VERSIONS);
        kc.securityProvider = new RestrictedPodSecurityProvider();
        kc.securityProvider.configure(new PlatformFeaturesAvailability(false, KubernetesVersion.MINIMAL_SUPPORTED_VERSION));

        StatefulSet sts = kc.generateStatefulSet(false, null, null, null);

        assertThat(sts.getSpec().getTemplate().getSpec().getSecurityContext(), is(nullValue()));
        assertThat(sts.getSpec().getTemplate().getSpec().getContainers().get(0).getSecurityContext().getAllowPrivilegeEscalation(), is(false));
        assertThat(sts.getSpec().getTemplate().getSpec().getContainers().get(0).getSecurityContext().getRunAsNonRoot(), is(true));
        assertThat(sts.getSpec().getTemplate().getSpec().getContainers().get(0).getSecurityContext().getSeccompProfile().getType(), is("RuntimeDefault"));
        assertThat(sts.getSpec().getTemplate().getSpec().getContainers().get(0).getSecurityContext().getCapabilities().getDrop(), is(List.of("ALL")));

    }
}
