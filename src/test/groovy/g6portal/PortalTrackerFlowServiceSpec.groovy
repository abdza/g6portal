package g6portal

import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import spock.lang.Specification
import org.hibernate.SessionFactory

@Integration
@Rollback
class PortalTrackerFlowServiceSpec extends Specification {

    PortalTrackerFlowService portalTrackerFlowService
    SessionFactory sessionFactory

    private Long setupData() {
        // TODO: Populate valid domain instances and return a valid ID
        //new PortalTrackerFlow(...).save(flush: true, failOnError: true)
        //new PortalTrackerFlow(...).save(flush: true, failOnError: true)
        //PortalTrackerFlow portalTrackerFlow = new PortalTrackerFlow(...).save(flush: true, failOnError: true)
        //new PortalTrackerFlow(...).save(flush: true, failOnError: true)
        //new PortalTrackerFlow(...).save(flush: true, failOnError: true)
        assert false, "TODO: Provide a setupData() implementation for this generated test suite"
        //portalTrackerFlow.id
    }

    void "test get"() {
        setupData()

        expect:
        portalTrackerFlowService.get(1) != null
    }

    void "test list"() {
        setupData()

        when:
        List<PortalTrackerFlow> portalTrackerFlowList = portalTrackerFlowService.list(max: 2, offset: 2)

        then:
        portalTrackerFlowList.size() == 2
        assert false, "TODO: Verify the correct instances are returned"
    }

    void "test count"() {
        setupData()

        expect:
        portalTrackerFlowService.count() == 5
    }

    void "test delete"() {
        Long portalTrackerFlowId = setupData()

        expect:
        portalTrackerFlowService.count() == 5

        when:
        portalTrackerFlowService.delete(portalTrackerFlowId)
        sessionFactory.currentSession.flush()

        then:
        portalTrackerFlowService.count() == 4
    }

    void "test save"() {
        when:
        assert false, "TODO: Provide a valid instance to save"
        PortalTrackerFlow portalTrackerFlow = new PortalTrackerFlow()
        portalTrackerFlowService.save(portalTrackerFlow)

        then:
        portalTrackerFlow.id != null
    }
}
