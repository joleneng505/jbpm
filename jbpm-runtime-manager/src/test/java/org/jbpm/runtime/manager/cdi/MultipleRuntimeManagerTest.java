package org.jbpm.runtime.manager.cdi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.inject.Inject;
import javax.persistence.EntityManagerFactory;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ArchivePaths;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jbpm.runtime.manager.impl.DefaultRuntimeEnvironment;
import org.jbpm.runtime.manager.impl.SimpleRuntimeEnvironment;
import org.jbpm.runtime.manager.util.TestUtil;
import org.jbpm.task.Status;
import org.jbpm.task.TaskService;
import org.jbpm.task.identity.DefaultUserGroupCallbackImpl;
import org.jbpm.task.identity.UserGroupCallbackManager;
import org.jbpm.task.query.TaskSummary;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kie.io.ResourceFactory;
import org.kie.io.ResourceType;
import org.kie.runtime.KieSession;
import org.kie.runtime.manager.Context;
import org.kie.runtime.manager.RuntimeManager;
import org.kie.runtime.manager.RuntimeManagerFactory;
import org.kie.runtime.manager.context.EmptyContext;
import org.kie.runtime.manager.context.ProcessInstanceIdContext;
import org.kie.runtime.process.ProcessInstance;

import bitronix.tm.resource.jdbc.PoolingDataSource;

@RunWith(Arquillian.class)
public class MultipleRuntimeManagerTest {
    
    @Deployment()
    public static Archive<?> createDeployment() {
        return ShrinkWrap.create(JavaArchive.class, "jbpm-runtime-manager.jar")
                .addPackage("org.jbpm.task")
                .addPackage("org.jbpm.task.wih") // work items org.jbpm.task.wih
                .addPackage("org.jbpm.task.annotations")
                .addPackage("org.jbpm.task.api")
                .addPackage("org.jbpm.task.impl")
                .addPackage("org.jbpm.task.events")
                .addPackage("org.jbpm.task.exception")
                .addPackage("org.jbpm.task.identity")
                .addPackage("org.jbpm.task.factories")
                .addPackage("org.jbpm.task.internals")
                .addPackage("org.jbpm.task.internals.lifecycle")
                .addPackage("org.jbpm.task.lifecycle.listeners")
                .addPackage("org.jbpm.task.query")
                .addPackage("org.jbpm.task.util")
                .addPackage("org.jbpm.task.commands") // This should not be required here
                .addPackage("org.jbpm.task.deadlines") // deadlines
                .addPackage("org.jbpm.task.deadlines.notifications.impl")
                .addPackage("org.jbpm.task.subtask")
                .addPackage("org.jbpm.runtime.manager")
                .addPackage("org.jbpm.runtime.manager.impl")
                .addPackage("org.jbpm.runtime.manager.impl.cdi.qualifier")
                .addPackage("org.jbpm.runtime.manager.impl.context")
                .addPackage("org.jbpm.runtime.manager.impl.factory")
                .addPackage("org.jbpm.runtime.manager.impl.jpa")
                .addPackage("org.jbpm.runtime.manager.impl.manager")
                .addPackage("org.jbpm.runtime.manager.mapper")
                .addPackage("org.jbpm.runtime.manager.impl.task")
                .addPackage("org.jbpm.runtime.manager.impl.tx") 
                .addPackage("org.jbpm.runtime.manager.util") // test utilities
                .addAsResource("jndi.properties","jndi.properties")
                .addAsManifestResource("META-INF/persistence.xml", ArchivePaths.create("persistence.xml"))
//                .addAsManifestResource("META-INF/Taskorm.xml", ArchivePaths.create("Taskorm.xml"))
                .addAsManifestResource("META-INF/beans.xml", ArchivePaths.create("beans.xml"));

    }
    
    private static PoolingDataSource pds;
    
    @BeforeClass
    public static void setup() {
        TestUtil.cleanupSingletonSessionId();
        pds = TestUtil.setupPoolingDataSource();
        Properties props = new Properties();
        props.setProperty("john", "user");
        
        UserGroupCallbackManager.getInstance().setCallback(new DefaultUserGroupCallbackImpl(props));
    }
    
    @AfterClass
    public static void teardown() {
        UserGroupCallbackManager.resetCallback();
        pds.close();
    }
    /*
     * end of initialization code, tests start here
     */

    @Inject
    private RuntimeManagerFactory managerFactory;
    
    @Inject
    private EntityManagerFactory emf;
    
    @Test
    public void testAllManagersManager() {
        assertNotNull(managerFactory);
        
        SimpleRuntimeEnvironment environment = new DefaultRuntimeEnvironment(emf);
        environment.addAsset(ResourceFactory.newClassPathResource("BPMN2-ScriptTask.bpmn2"), ResourceType.BPMN2);
        environment.addAsset(ResourceFactory.newClassPathResource("BPMN2-UserTask.bpmn2"), ResourceType.BPMN2);
        
        RuntimeManager manager = managerFactory.newSingletonRuntimeManager(environment);
        testProcessStartOnManager(manager, EmptyContext.get());
        
        environment = new DefaultRuntimeEnvironment(emf);
        environment.addAsset(ResourceFactory.newClassPathResource("BPMN2-ScriptTask.bpmn2"), ResourceType.BPMN2);
        environment.addAsset(ResourceFactory.newClassPathResource("BPMN2-UserTask.bpmn2"), ResourceType.BPMN2);
        
        manager = managerFactory.newPerRequestRuntimeManager(environment);
        testProcessStartOnManager(manager, EmptyContext.get());

        environment = new DefaultRuntimeEnvironment(emf);
        environment.addAsset(ResourceFactory.newClassPathResource("BPMN2-ScriptTask.bpmn2"), ResourceType.BPMN2);
        environment.addAsset(ResourceFactory.newClassPathResource("BPMN2-UserTask.bpmn2"), ResourceType.BPMN2);
        
        manager = managerFactory.newPerProcessInstanceRuntimeManager(environment);
        testProcessStartOnManager(manager, ProcessInstanceIdContext.get());

    }    
    
    
    private void testProcessStartOnManager(RuntimeManager manager, Context context) {
        assertNotNull(manager);
        
        org.kie.runtime.manager.Runtime<TaskService> runtime = manager.getRuntime(context);
        assertNotNull(runtime);
        
        KieSession ksession = runtime.getKieSession();
        assertNotNull(ksession);
        
        ProcessInstance processInstance = ksession.startProcess("UserTask");
        assertNotNull(processInstance);
        
        List<Status> statuses = new ArrayList<Status>();
        statuses.add(Status.Reserved);
        List<TaskSummary> tasks = runtime.getTaskService().getTasksOwned("john", statuses, "en-UK");
        assertNotNull(tasks);
        assertEquals(1, tasks.size());
        
        runtime.getTaskService().start(tasks.get(0).getId(), "john");
        
        runtime.getTaskService().complete(tasks.get(0).getId(), "john", null);
        
        processInstance = ksession.getProcessInstance(processInstance.getId());
        assertNull(processInstance);
    }
    
}
