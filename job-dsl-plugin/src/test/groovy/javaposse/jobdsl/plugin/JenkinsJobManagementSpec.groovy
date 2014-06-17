package javaposse.jobdsl.plugin

import com.google.common.io.Resources
import hudson.EnvVars
import hudson.model.AbstractBuild
import hudson.model.Failure
import javaposse.jobdsl.dsl.ConfigurationMissingException
import javaposse.jobdsl.dsl.NameNotProvidedException
import javaposse.jobdsl.dsl.helpers.PropertiesContext
import javaposse.jobdsl.plugin.api.ContextExtensionPoint
import org.custommonkey.xmlunit.XMLUnit
import org.junit.Rule
import org.jvnet.hudson.test.JenkinsRule
import spock.lang.Specification

import static com.google.common.base.Charsets.UTF_8
import static com.google.common.io.Resources.getResource
import static hudson.model.Result.UNSTABLE

class JenkinsJobManagementSpec extends Specification {
    @Rule
    JenkinsRule jenkinsRule = new JenkinsRule()

    ByteArrayOutputStream buffer = new ByteArrayOutputStream()
    AbstractBuild build = Mock(AbstractBuild)
    JenkinsJobManagement jobManagement = new JenkinsJobManagement(new PrintStream(buffer), new EnvVars(), build)

    def 'getItemNameFromFullName'() {
        expect:
        JenkinsJobManagement.getItemNameFromFullName(fullName) == itemName

        where:
        fullName     || itemName
        'a/b/c'      || 'c'
        'folder/job' || 'job'
        'myjob'      || 'myjob'
    }

    def 'createOrUpdateView without name'() {
        when:
        jobManagement.createOrUpdateView(null, '<View/>', true)

        then:
        thrown(NameNotProvidedException)

        when:
        jobManagement.createOrUpdateView('', '<View/>', true)

        then:
        thrown(NameNotProvidedException)
    }

    def 'createOrUpdateView without config'() {
        when:
        jobManagement.createOrUpdateView('test', null, true)

        then:
        thrown(ConfigurationMissingException)

        when:
        jobManagement.createOrUpdateView('test', null, true)

        then:
        thrown(ConfigurationMissingException)
    }

    def 'createOrUpdateView with invalid name'() {
        when:
        jobManagement.createOrUpdateView('t<e*st', '<View/>', true)

        then:
        thrown(Failure)
    }

    def 'checkMinimumPluginVersion not installed'() {
        when:
        jobManagement.requireMinimumPluginVersion('foo', '1.2.3')

        then:
        1 * build.setResult(UNSTABLE)
        buffer.size() > 0
    }

    def 'checkMinimumPluginVersion too old'() {
        when:
        jobManagement.requireMinimumPluginVersion('ldap', '20.0')

        then:
        1 * build.setResult(UNSTABLE)
        buffer.size() > 0
    }

    def 'checkMinimumPluginVersion success'() {
        when:
        jobManagement.requireMinimumPluginVersion('ldap', '1.1')

        then:
        0 * build.setResult(UNSTABLE)
        buffer.size() == 0
    }

    def 'callExtension not found'() {
        when:
        Node result = jobManagement.callExtension('foo', PropertiesContext)

        then:
        result == null
    }

    def 'callExtension with no args'() {
        when:
        Node result = jobManagement.callExtension('test', PropertiesContext)

        then:
        isXmlIdentical('extension.xml', result)
    }

    def 'callExtension defined twice'() {
        when:
        jobManagement.callExtension('twice', PropertiesContext)

        then:
        Exception e = thrown(ExtensionPointException)
        e.message.contains(TestContextExtensionPoint.name)
        e.message.contains(TestContextExtensionPoint2.name)
    }

    def 'callExtension with object result'() {
        when:
        Node result = jobManagement.callExtension('testComplexObject', PropertiesContext, 'foo', 42, true)

        then:
        isXmlIdentical('extension.xml', result)
    }

    def 'callExtension with closure'() {
        setup:
        Closure closure = {
            value1('foo')
            value2(42)
            value3(true)
        }

        when:
        Node result = jobManagement.callExtension('withNestedContext', PropertiesContext, closure)

        then:
        isXmlIdentical('extension.xml', result)
    }

    def 'extension is being notified'() {
        when:
        jobManagement.createOrUpdateConfig('test-123', loadResource('config.xml'), true)

        then:
        ContextExtensionPoint.all().get(TestContextExtensionPoint).isItemCreated('test-123')

        when:
        jobManagement.createOrUpdateConfig('test-123', loadResource('config2.xml'), false)

        then:
        ContextExtensionPoint.all().get(TestContextExtensionPoint).isItemUpdated('test-123')
    }

    private static boolean isXmlIdentical(String expected, Node actual) throws Exception {
        XMLUnit.ignoreWhitespace = true
        XMLUnit.compareXML(loadResource(expected), nodeToString(actual)).identical()
    }

    private static String nodeToString(Node node) {
        StringWriter writer = new StringWriter()
        new XmlNodePrinter(new PrintWriter(writer)).print(node)
        writer.toString()
    }

    private static String loadResource(String resourceName) {
        Resources.toString(getResource(resourceName), UTF_8)
    }
}
