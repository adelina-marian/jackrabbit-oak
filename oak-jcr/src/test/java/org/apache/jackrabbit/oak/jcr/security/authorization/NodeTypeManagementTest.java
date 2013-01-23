/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.jcr.security.authorization;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import javax.jcr.AccessDeniedException;
import javax.jcr.ImportUUIDBehavior;
import javax.jcr.Node;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.jcr.security.Privilege;

import org.apache.jackrabbit.test.NotExecutableException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * NodeTypeManagementTest... TODO
 *
 * copied from jr2.x NodeTypeTest
 */
@Ignore("OAK-51")
public class NodeTypeManagementTest extends AbstractEvaluationTest {

    private Node childNode;
    private String mixinName;

    @Override
    @Before
    protected void setUp() throws Exception {
        super.setUp();

        Node child = testRootNode.addNode(nodeName2);
        if (child.isNodeType(mixReferenceable) || !child.canAddMixin(mixReferenceable)) {
            throw new NotExecutableException();
        }
        superuser.save();
        
        mixinName = getTestSession().getNamespacePrefix(NS_MIX_URI) + ":referenceable";
        childNode = getTestSession().getNode(child.getPath());
    }

    @Test
    public void testCanAddMixin() throws Exception {
        checkReadOnly(childNode.getPath());

        assertFalse(childNode.canAddMixin(mixinName));

        modify(childNode.getPath(), Privilege.JCR_NODE_TYPE_MANAGEMENT, true);
        assertTrue(childNode.canAddMixin(mixinName));

        modify(childNode.getPath(), Privilege.JCR_NODE_TYPE_MANAGEMENT, false);
        assertFalse(childNode.canAddMixin(mixinName));
    }

    @Test
    public void testAddMixin() throws Exception {
        checkReadOnly(childNode.getPath());

        try {
            childNode.addMixin(mixinName);
            superuser.save();
            fail("TestSession does not have sufficient privileges to add a mixin type.");
        } catch (AccessDeniedException e) {
            // success
        }

        modify(childNode.getPath(), Privilege.JCR_NODE_TYPE_MANAGEMENT, true);
        childNode.addMixin(mixinName);
        superuser.save();
    }

    @Test
    public void testRemoveMixin() throws Exception {
        ((Node) superuser.getItem(childNode.getPath())).addMixin(mixinName);
        superuser.save();

        checkReadOnly(childNode.getPath());

        try {
            childNode.removeMixin(mixinName);
            superuser.save();
            fail("TestSession does not have sufficient privileges to remove a mixin type.");
        } catch (AccessDeniedException e) {
            // success
        }

        modify(childNode.getPath(), Privilege.JCR_NODE_TYPE_MANAGEMENT, true);
        childNode.removeMixin(mixinName);
        superuser.save();
    }

    @Test
    public void testSetPrimaryType() throws Exception {
        Node child = (Node) superuser.getItem(childNode.getPath());
        String ntName = child.getPrimaryNodeType().getName();

        String changedNtName = "nt:folder";
        child.setPrimaryType(changedNtName);
        superuser.save();

        try {
            checkReadOnly(childNode.getPath());

            try {
                childNode.setPrimaryType(ntName);
                superuser.save();
                fail("TestSession does not have sufficient privileges to change the primary type.");
            } catch (AccessDeniedException e) {
                // success
                getTestSession().refresh(false); // TODO: see JCR-1916
            }

            modify(childNode.getPath(), Privilege.JCR_NODE_TYPE_MANAGEMENT, true);
            childNode.setPrimaryType(ntName);
            superuser.save();

        } finally {
            if (!ntName.equals(child.getPrimaryNodeType().getName())) {
                child.setPrimaryType(ntName);
                superuser.save();
            }
        }
    }

    /**
     * Test difference between common jcr:write privilege an rep:write privilege
     * that includes the ability to set the primary node type upon child node
     * creation.
     */
    @Test
    public void testAddNode() throws Exception {
        checkReadOnly(childNode.getPath());

        // with simple write privilege a child node can be added BUT no
        // node type must be specified.
        modify(childNode.getPath(), Privilege.JCR_WRITE, true);
        addChildNode(false);
        try {
            addChildNode(true);
            fail("Missing privilege jcr:nodeTypeManagement.");
        } catch (AccessDeniedException e) {
            // success
        }

        // adding jcr:nodeTypeManagement privilege will allow to use any
        // variant of Node.addNode.
        modify(childNode.getPath(), Privilege.JCR_NODE_TYPE_MANAGEMENT, true);
        addChildNode(false);
        addChildNode(true);
    }

    private void addChildNode(boolean specifyNodeType) throws Exception {
        Node n = null;
        try {
            n = (specifyNodeType) ? childNode.addNode(nodeName3, testNodeType) : childNode.addNode(nodeName3);
        } finally {
            if (n != null) {
                n.remove();
                superuser.save();
            }
        }
    }

    @Test
    public void testCopy() throws Exception {
        Workspace wsp = getTestSession().getWorkspace();
        String parentPath = childNode.getParent().getPath();
        String srcPath = childNode.getPath();
        String destPath = parentPath + '/' + nodeName3;

        checkReadOnly(parentPath);
        try {
            wsp.copy(srcPath, destPath);
            fail("Missing write privilege.");
        } catch (AccessDeniedException e) {
            // success
        }

        // with simple write privilege copying a node is not allowed.
        modify(parentPath, Privilege.JCR_WRITE, true);
        try {
            wsp.copy(srcPath, destPath);
            fail("Missing privilege jcr:nodeTypeManagement.");
        } catch (AccessDeniedException e) {
            // success
        }

        // adding jcr:nodeTypeManagement privilege will grant permission to copy.
        modify(parentPath, REP_WRITE, true);
        wsp.copy(srcPath, destPath);
    }

    @Test
    public void testWorkspaceMove() throws Exception {
        Workspace wsp = getTestSession().getWorkspace();
        String parentPath = childNode.getParent().getPath();
        String srcPath = childNode.getPath();
        String destPath = parentPath + '/' + nodeName3;

        checkReadOnly(parentPath);
        try {
            wsp.move(srcPath, destPath);
            fail("Missing write privilege.");
        } catch (AccessDeniedException e) {
            // success
        }

        // with simple write privilege moving a node is not allowed.
        modify(parentPath, Privilege.JCR_WRITE, true);
        try {
            wsp.move(srcPath, destPath);
            fail("Missing privilege jcr:nodeTypeManagement.");
        } catch (AccessDeniedException e) {
            // success
        }

        // adding jcr:nodeTypeManagement privilege will grant permission to move.
        modify(parentPath, REP_WRITE, true);
        wsp.move(srcPath, destPath);
    }

    @Test
    public void testSessionMove() throws Exception {
        Session s = getTestSession();
        String parentPath = childNode.getParent().getPath();
        String srcPath = childNode.getPath();
        String destPath = parentPath + '/' + nodeName3;

        checkReadOnly(parentPath);
        try {
            s.move(srcPath, destPath);
            s.save();
            fail("Missing write privilege.");
        } catch (AccessDeniedException e) {
            // success
        }

        // with simple write privilege moving a node is not allowed.
        modify(parentPath, Privilege.JCR_WRITE, true);
        try {
            s.move(srcPath, destPath);
            s.save();
            fail("Missing privilege jcr:nodeTypeManagement.");
        } catch (AccessDeniedException e) {
            // success
        }

        // adding jcr:nodeTypeManagement privilege will grant permission to move.
        modify(parentPath, REP_WRITE, true);
        s.move(srcPath, destPath);
        s.save();
    }

    @Test
    public void testSessionImportXML() throws Exception {
        Session s = getTestSession();
        String parentPath = childNode.getPath();

        checkReadOnly(parentPath);
        try {
            s.importXML(parentPath, getXmlForImport(), ImportUUIDBehavior.IMPORT_UUID_CREATE_NEW);
            s.save();
            fail("Missing write privilege.");
        } catch (AccessDeniedException e) {
            // success
        } finally {
            s.refresh(false);
        }

        // with simple write privilege moving a node is not allowed.
        modify(parentPath, Privilege.JCR_WRITE, true);
        try {
            s.importXML(parentPath, getXmlForImport(), ImportUUIDBehavior.IMPORT_UUID_CREATE_NEW);
            s.save();
            fail("Missing privilege jcr:nodeTypeManagement.");
        } catch (AccessDeniedException e) {
            // success
        } finally {
            s.refresh(false);
        }

        // adding jcr:nodeTypeManagement privilege will grant permission to move.
        modify(parentPath, REP_WRITE, true);
        s.importXML(parentPath, getXmlForImport(), ImportUUIDBehavior.IMPORT_UUID_CREATE_NEW);
        s.save();
    }

    @Test
    public void testWorkspaceImportXML() throws Exception {
        Workspace wsp = getTestSession().getWorkspace();
        String parentPath = childNode.getPath();

        checkReadOnly(parentPath);
        try {
            wsp.importXML(parentPath, getXmlForImport(), ImportUUIDBehavior.IMPORT_UUID_CREATE_NEW);
            fail("Missing write privilege.");
        } catch (AccessDeniedException e) {
            // success
        }

        // with simple write privilege moving a node is not allowed.
        modify(parentPath, Privilege.JCR_WRITE, true);
        try {
            wsp.importXML(parentPath, getXmlForImport(), ImportUUIDBehavior.IMPORT_UUID_CREATE_NEW);
            fail("Missing privilege jcr:nodeTypeManagement.");
        } catch (AccessDeniedException e) {
            // success
        }

        // adding jcr:nodeTypeManagement privilege will grant permission to move.
        modify(parentPath, REP_WRITE, true);
        wsp.importXML(parentPath, getXmlForImport(), ImportUUIDBehavior.IMPORT_UUID_CREATE_NEW);
    }

    /**
     * Simple XML for testing permissions upon import.
     */
    private InputStream getXmlForImport() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<sv:node xmlns:nt=\"http://www.jcp.org/jcr/nt/1.0\"" +
                "         xmlns:sv=\"http://www.jcp.org/jcr/sv/1.0\"" +
                "         xmlns:jcr=\"http://www.jcp.org/jcr/1.0\"" +
                "         sv:name=\"" + nodeName3 + "\">" +
                "    <sv:property sv:name=\"jcr:primaryType\" sv:type=\"Name\">" +
                "        <sv:value>" + testNodeType + "</sv:value>" +
                "    </sv:property>" +
                "</sv:node>";
        return new ByteArrayInputStream(xml.getBytes());
    }
}