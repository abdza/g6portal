package g6portal

import groovy.transform.CompileStatic
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig
import org.jasypt.iv.RandomIvGenerator
import org.jasypt.salt.RandomSaltGenerator
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource

/**
 * GORM/Hibernate builds its DataSource from the Environment very early during
 * context refresh - before jasypt-spring-boot's own BeanFactoryPostProcessor
 * (ordered at LOWEST_PRECEDENCE) would get a chance to decrypt ENC(...)
 * values, and generically wrapping every PropertySource (the jasypt-spring-
 * boot default approach) breaks Grails' relaxed binding of the nested
 * hibernate.* config map (non-String values come back null, NPE in
 * HibernateConnectionSourceSettings). So instead of wrapping the whole
 * Environment, this targets just dataSource.password: decrypt it once here
 * and push it in as a single highest-priority property override.
 *
 * Master key comes from the JASYPT_ENCRYPTOR_PASSWORD env var (mapped to
 * jasypt.encryptor.password by Spring's relaxed binding) - never commit it.
 */
@CompileStatic
class JasyptEarlyDecryptionInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String ENC_PREFIX = 'ENC('
    private static final String ENC_SUFFIX = ')'

    @Override
    void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment()
        String rawPassword = environment.getProperty('dataSource.password')
        if (!rawPassword || !isEncrypted(rawPassword)) {
            return
        }

        String masterKey = environment.getProperty('jasypt.encryptor.password')
        if (!masterKey) {
            throw new IllegalStateException(
                'dataSource.password is ENC(...) but no JASYPT_ENCRYPTOR_PASSWORD ' +
                '(jasypt.encryptor.password) was supplied to decrypt it.')
        }

        String decrypted = buildEncryptor(masterKey).decrypt(unwrap(rawPassword))

        def overrides = new MapPropertySource('jasypt-decrypted-datasource-password',
                ['dataSource.password': decrypted] as Map<String, Object>)
        environment.getPropertySources().addFirst(overrides)
    }

    private static boolean isEncrypted(String value) {
        value.startsWith(ENC_PREFIX) && value.endsWith(ENC_SUFFIX)
    }

    private static String unwrap(String value) {
        value.substring(ENC_PREFIX.length(), value.length() - ENC_SUFFIX.length())
    }

    private static StandardPBEStringEncryptor buildEncryptor(String masterKey) {
        SimpleStringPBEConfig config = new SimpleStringPBEConfig()
        config.setPassword(masterKey)
        config.setAlgorithm('PBEWITHHMACSHA512ANDAES_256')
        config.setKeyObtentionIterations(1000)
        config.setPoolSize(1)
        config.setProviderName('SunJCE')
        config.setSaltGenerator(new RandomSaltGenerator())
        config.setIvGenerator(new RandomIvGenerator())
        config.setStringOutputType('base64')

        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor()
        encryptor.setConfig(config)
        encryptor
    }
}
