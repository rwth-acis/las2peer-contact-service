package i5.las2peer.services.contactService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import i5.las2peer.api.p2p.ServiceNameVersion;
import i5.las2peer.connectors.webConnector.WebConnector;
import i5.las2peer.connectors.webConnector.client.ClientResponse;
import i5.las2peer.connectors.webConnector.client.MiniClient;
import i5.las2peer.p2p.PastryNodeImpl;
import i5.las2peer.persistency.EnvelopeVersion;
import i5.las2peer.persistency.SharedStorage.STORAGE_MODE;
import i5.las2peer.security.AgentImpl;
import i5.las2peer.security.ServiceAgentImpl;
import i5.las2peer.security.UserAgentImpl;
import i5.las2peer.testing.MockAgentFactory;
import i5.las2peer.testing.TestSuite;

/**
 * Example Test Class demonstrating a basic JUnit test structure.
 *
 */
public class ServiceTest {

	private static final String HTTP_ADDRESS = "http://127.0.0.1";
	private static final int HTTP_PORT = WebConnector.DEFAULT_HTTP_PORT;

	private static ArrayList<PastryNodeImpl> nodes;
	private static PastryNodeImpl node;
	private static WebConnector connector;
	private static ByteArrayOutputStream logStream;

	private static UserAgentImpl agentAdam;
	private static UserAgentImpl agentEve;
	private static UserAgentImpl agentAbel;
	private static final String passAdam = "adamspass";
	private static final String passEve = "evespass";
	private static final String passAbel = "abelspass";

	private static final String mainPath = "contactservice/";
	private static ServiceAgentImpl testService;
	private static ServiceAgentImpl testService2;

	/**
	 * Called before the tests start.
	 * 
	 * Sets up the node and initializes connector and users that can be used throughout the tests.
	 * 
	 * @throws Exception
	 */
	@Before
	public void startServer() throws Exception {

		// start node
		nodes = TestSuite.launchNetwork(1, STORAGE_MODE.FILESYSTEM, true);
		node = nodes.get(0);
		agentAdam = MockAgentFactory.getAdam();
		agentAdam.unlock(passAdam);
		agentEve = MockAgentFactory.getEve();
		agentEve.unlock(passEve);
		agentAbel = MockAgentFactory.getAbel();
		agentAbel.unlock(passAbel);
		node.storeAgent(agentAdam);
		node.storeAgent(agentEve);
		node.storeAgent(agentAbel);

		// during testing, the specified service version does not matter
		testService = ServiceAgentImpl.createServiceAgent(
				ServiceNameVersion.fromString("i5.las2peer.services.contactService.ContactService@0.2.1"), "a pass");
		testService.unlock("a pass");

		testService2 = ServiceAgentImpl.createServiceAgent(
				ServiceNameVersion.fromString("i5.las2peer.services.userInformationService.UserInformationService@0.2"),
				"a pass");
		testService2.unlock("a pass");

		node.registerReceiver(testService);
		node.registerReceiver(testService2);

		// start connector
		logStream = new ByteArrayOutputStream();

		connector = new WebConnector(true, HTTP_PORT, false, 1000);
		connector.setLogStream(new PrintStream(logStream));
		connector.start(node);
		Thread.sleep(1000); // wait a second for the connector to become ready
		agentAdam = MockAgentFactory.getAdam();
		agentEve = MockAgentFactory.getEve();
		agentAbel = MockAgentFactory.getAbel();
	}

	/**
	 * Called after the tests have finished. Shuts down the server and prints out the connector log file for reference.
	 * 
	 * @throws Exception
	 */
	@After
	public void shutDownServer() throws Exception {

		connector.stop();
		node.shutDown();

		connector = null;

		node = null;

		System.out.println("Connector-Log:");
		System.out.println("--------------");

		System.out.println(logStream.toString());

	}

	/**
	 * 
	 * Test adding a contact
	 * 
	 */

	@Test
	public void testAddRemoveContact() {
		MiniClient c = new MiniClient();
		c.setAddressPort(HTTP_ADDRESS, HTTP_PORT);

		try {
			c.setLogin(agentAdam.getIdentifier(), passAdam);

			// Test get contact name

			ClientResponse result = c.sendRequest("GET", mainPath + "name/" + agentAdam.getIdentifier(), "");
			assertEquals(200, result.getHttpCode());
			assertTrue(result.getResponse().trim().contains("adam"));
			System.out.println("Result of 'testAddRemoveContact': " + result.getResponse().trim());

			result = c.sendRequest("GET", mainPath + "name/1337", "");
			assertEquals(404, result.getHttpCode());
			System.out.println("Result of 'testAddRemoveContact': " + result.getResponse().trim());

			// Add a contact
			result = c.sendRequest("POST", mainPath + "eve1st", "");
			assertEquals(200, result.getHttpCode());
			assertTrue(result.getResponse().trim().contains("Contact added"));
			System.out.println("Result of 'testAddRemoveContact': " + result.getResponse().trim());

			// Add the same contact again
			ClientResponse result2 = c.sendRequest("POST", mainPath + "eve1st", "");
			assertEquals(400, result2.getHttpCode());
			assertTrue(result2.getResponse().trim().contains("Contact already in list"));
			System.out.println("Result of 'testAddRemoveContact': " + result2.getResponse().trim());

			// Add a contact that does not exist
			ClientResponse result3 = c.sendRequest("POST", mainPath + "eve2nd", "");
			assertEquals(404, result3.getHttpCode());
			assertTrue(result3.getResponse().trim().contains("Agent does not exist."));
			System.out.println("Result of 'testAddRemoveContact': " + result3.getResponse().trim());

			// Remove Contact
			ClientResponse result4 = c.sendRequest("DELETE", mainPath + "eve1st", "");
			assertEquals(200, result4.getHttpCode());
			assertTrue(result4.getResponse().trim().contains("Contact removed"));
			System.out.println("Result of 'testAddRemoveContact': " + result4.getResponse().trim());

			// Try to remove contact again
			ClientResponse result5 = c.sendRequest("DELETE", mainPath + "eve1st", "");
			assertEquals(404, result5.getHttpCode());
			assertTrue(result5.getResponse().trim().contains("User is not one of your contacts."));
			System.out.println("Result of 'testAddRemoveContact': " + result5.getResponse().trim());

			// Remove user that does not exist
			ClientResponse result6 = c.sendRequest("DELETE", mainPath + "eve2nd", "");
			assertEquals(404, result6.getHttpCode());
			assertTrue(result6.getResponse().trim().contains("Agent does not exist"));
			System.out.println("Result of 'testAddRemoveContact': " + result6.getResponse().trim());
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
	}

	@Test
	public void testGetContacts() {
		MiniClient c = new MiniClient();
		c.setAddressPort(HTTP_ADDRESS, HTTP_PORT);

		try {
			c.setLogin(agentAdam.getIdentifier(), passAdam);

			// get Contacts with no contacts
			ClientResponse result = c.sendRequest("GET", mainPath, "", "text/plain", "application/json",
					new HashMap<String, String>());
			assertEquals(200, result.getHttpCode());
			assertTrue(result.getResponse().trim().contains("{}"));
			System.out.println("Result of 'testGetContacts': " + result.getResponse().trim());

			// with one contact
			c.sendRequest("POST", mainPath + "eve1st", "");
			ClientResponse result2 = c.sendRequest("GET", mainPath, "", "text/plain", "application/json",
					new HashMap<String, String>());
			assertEquals(200, result2.getHttpCode());
			assertTrue(result2.getResponse().trim().contains("eve1st"));
			System.out.println("Result of 'testGetContacts': " + result2.getResponse().trim());

			// with more than one contact
			c.sendRequest("POST", mainPath + "abel", "");
			ClientResponse result3 = c.sendRequest("GET", mainPath, "", "text/plain", "application/json",
					new HashMap<String, String>());
			assertEquals(200, result3.getHttpCode());
			assertTrue(result3.getResponse().trim().contains("eve1st"));
			assertTrue(result3.getResponse().trim().contains("abel"));
			System.out.println("Result of 'testGetContacts': " + result3.getResponse().trim());

			// Remove Contacts
			c.sendRequest("DELETE", mainPath + "eve1st", "");
			c.sendRequest("DELETE", mainPath + "abel", "");

			// Check if list is empty again
			ClientResponse result4 = c.sendRequest("GET", mainPath, "", "text/plain", "application/json",
					new HashMap<String, String>());
			assertEquals(200, result4.getHttpCode());
			assertTrue(result4.getResponse().trim().contains("{}"));
			System.out.println("Result of 'testGetContacts': " + result4.getResponse().trim());

		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
	}

	@Test
	public void testGetContactsWithUnknownAgent() {
		MiniClient c = new MiniClient();
		c.setAddressPort(HTTP_ADDRESS, HTTP_PORT);

		try {
			c.setLogin(agentAdam.getIdentifier(), passAdam);
			agentAdam.unlock(passAdam);
			String identifier = "contacts_" + agentAdam.getIdentifier();
			ContactContainer cc = new ContactContainer();
			cc.addContact("1337");
			createEnvelopeWithContent(identifier, agentAdam, cc);

			ClientResponse result = c.sendRequest("GET", mainPath, "", "text/plain", "application/json",
					new HashMap<String, String>());
			assertEquals(200, result.getHttpCode());
			assertTrue(result.getResponse().trim().contains("{}"));
			System.out.println("Result of 'testGetContactsWithUnknownAgent': " + result.getResponse().trim());
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
	}

	@Test
	public void testGroups() {
		MiniClient c = new MiniClient();
		c.setAddressPort(HTTP_ADDRESS, HTTP_PORT);

		try {
			c.setLogin(agentAdam.getIdentifier(), passAdam);

			// Add a group
			ClientResponse result = c.sendRequest("POST", mainPath + "groups/testGroup", "");
			assertEquals(200, result.getHttpCode());
			System.out.println("Result of 'testGroups': " + result.getResponse().trim());

			result = c.sendRequest("POST", mainPath + "groups/testGroup", "");
			assertEquals(400, result.getHttpCode());
			System.out.println("Result of 'testGroups': " + result.getResponse().trim());

			result = c.sendRequest("POST", mainPath + "groups/anotherGroup", "");
			assertEquals(200, result.getHttpCode());
			System.out.println("Result of 'testGroups': " + result.getResponse().trim());

			// Check groups
			ClientResponse result2 = c.sendRequest("GET", mainPath + "groups", "", "text/plain", "application/json",
					new HashMap<String, String>());
			assertEquals(200, result2.getHttpCode());
			assertTrue(result2.getResponse().contains("testGroup"));
			System.out.println("Result of 'testGroups': " + result2.getResponse().trim());

			// Check group member
			ClientResponse result3 = c.sendRequest("GET", mainPath + "groups/testGroup/member", "", "text/plain",
					"application/json", new HashMap<String, String>());
			assertEquals(200, result3.getHttpCode());
			System.out.println("Result of 'testGroups': " + result3.getResponse().trim());

			// try another agent
			c.setLogin(agentAbel.getIdentifier(), passAbel);

			ClientResponse result4 = c.sendRequest("GET", mainPath + "groups", "");
			assertEquals(200, result4.getHttpCode());
			assertTrue(result4.getResponse().contains("{}"));
			System.out.println("Result of 'testGroups': " + result4.getResponse().trim());

			result4 = c.sendRequest("GET", mainPath + "groups/testGroup", "");
			assertEquals(400, result4.getHttpCode());
			assertTrue(result4.getResponse().contains("{}"));
			System.out.println("Result of 'testGroups': " + result4.getResponse().trim());

			// add agent with first agent
			c.setLogin(agentAdam.getIdentifier(), passAdam);

			ClientResponse result5 = c.sendRequest("POST", mainPath + "groups/testGroup/member/abel", "");
			assertEquals(200, result5.getHttpCode());
			System.out.println("Result of 'testGroups': " + result5.getResponse().trim());

			// add agent who does not exist
			ClientResponse result6 = c.sendRequest("POST", mainPath + "groups/testGroup/member/abel1337", "");
			assertEquals(404, result6.getHttpCode());
			System.out.println("Result of 'testGroups': " + result6.getResponse().trim());

			// Check group member
			ClientResponse result7 = c.sendRequest("GET", mainPath + "groups/testGroup/member", "", "text/plain",
					"application/json", new HashMap<String, String>());
			assertEquals(200, result7.getHttpCode());
			assertTrue(result7.getResponse().contains("abel"));
			System.out.println("Result of 'testGroups': " + result7.getResponse().trim());

			ClientResponse result77 = c.sendRequest("GET", mainPath + "groups/testGroupWhichDoesNotExist/member", "");
			assertEquals(400, result77.getHttpCode());
			System.out.println("Result of 'testGroups': " + result77.getResponse().trim());

			// now check with other agent again

			c.setLogin(agentAbel.getIdentifier(), passAbel);

			ClientResponse result8 = c.sendRequest("GET", mainPath + "groups/testGroup/member", "");
			assertEquals(200, result8.getHttpCode());
			assertTrue(result8.getResponse().contains("abel"));
			assertTrue(result8.getResponse().contains("adam"));
			System.out.println("Result of 'testGroups': " + result8.getResponse().trim());

			ClientResponse result9 = c.sendRequest("GET", mainPath + "groups", "");
			assertEquals(200, result9.getHttpCode());
			assertTrue(result9.getResponse().contains("testGroup"));
			System.out.println("Result of 'testGroups': " + result9.getResponse().trim());

			result9 = c.sendRequest("GET", mainPath + "groups/testGroup", "");
			assertEquals(200, result9.getHttpCode());
			System.out.println("Result of 'testGroups': " + result9.getResponse().trim());

			ClientResponse result10 = c.sendRequest("DELETE", mainPath + "groups/testGroup/member/adam", "");
			assertEquals(200, result10.getHttpCode());

			ClientResponse result11 = c.sendRequest("DELETE", mainPath + "groups/testGroup", "");
			assertEquals(200, result11.getHttpCode());

			// remove again should not work
			result11 = c.sendRequest("DELETE", mainPath + "groups/testGroup", "");
			assertEquals(404, result11.getHttpCode());

			result9 = c.sendRequest("GET", mainPath + "groups/testGroup", "");
			assertEquals(404, result9.getHttpCode());
			System.out.println("Result of 'testGroups': " + result9.getResponse().trim());

		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
	}

	@Test
	public void testBlockGroups() {
		MiniClient c = new MiniClient();
		c.setAddressPort(HTTP_ADDRESS, HTTP_PORT);

		try {
			c.setLogin(agentAdam.getIdentifier(), passAdam);
			agentEve.unlock(passEve);
			String identifier = "groups_testGroup";
			createEnvelope(identifier, agentEve);
			ClientResponse result = c.sendRequest("POST", mainPath + "groups/testGroup", "");
			assertEquals(400, result.getHttpCode());
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
	}

	@Test
	public void testRemoveGroupMember() {
		MiniClient c = new MiniClient();
		c.setAddressPort(HTTP_ADDRESS, HTTP_PORT);

		try {
			c.setLogin(agentAdam.getIdentifier(), passAdam);
			agentAdam.unlock(passAdam);
			String identifier = "groups_testGroup";
			ContactContainer cc = new ContactContainer();
			cc.addGroup("testGroup", "1337");
			createEnvelopeWithContent(identifier, agentAdam, cc);
			ClientResponse result = c.sendRequest("DELETE", mainPath + "groups/testGroup/member/adam", "");
			assertEquals(404, result.getHttpCode());
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
	}

	@Test
	public void testAddressBook() {
		MiniClient c = new MiniClient();
		c.setAddressPort(HTTP_ADDRESS, HTTP_PORT);

		try {
			c.setLogin(agentAdam.getIdentifier(), passAdam);

			// Add a contact
			ClientResponse result = c.sendRequest("POST", mainPath + "addressbook", "");
			assertEquals(200, result.getHttpCode());
			System.out.println("Result of 'testAddressBook': " + result.getResponse().trim());

			c.setLogin(agentEve.getIdentifier(), passEve);
			ClientResponse result2 = c.sendRequest("POST", mainPath + "addressbook", "");
			assertEquals(200, result2.getHttpCode());
			System.out.println("Result of 'testAddressBook': " + result2.getResponse().trim());

			result2 = c.sendRequest("POST", mainPath + "addressbook", "");
			assertEquals(400, result2.getHttpCode());
			System.out.println("Result of 'testAddressBook': " + result2.getResponse().trim());

			c.setLogin(agentAdam.getIdentifier(), passAdam);

			// Get contacts
			ClientResponse result3 = c.sendRequest("GET", mainPath + "addressbook", "");
			assertEquals(200, result3.getHttpCode());
			System.out.println("Result of 'testAddressBook': " + result3.getResponse().trim());

			// Remove contact
			ClientResponse result4 = c.sendRequest("DELETE", mainPath + "addressbook", "");
			assertEquals(200, result4.getHttpCode());
			System.out.println("Result of 'testAddressBook': " + result4.getResponse().trim());

			result4 = c.sendRequest("DELETE", mainPath + "addressbook", "");
			assertEquals(404, result4.getHttpCode());
			System.out.println("Result of 'testAddressBook': " + result4.getResponse().trim());

			c.setLogin(agentEve.getIdentifier(), passEve);
			ClientResponse result5 = c.sendRequest("GET", mainPath + "addressbook", "");
			assertEquals(200, result5.getHttpCode());
			System.out.println("Result of 'testAddressBook': " + result5.getResponse().trim());

			ClientResponse result6 = c.sendRequest("GET", mainPath + "addressbook", "");
			assertEquals(200, result6.getHttpCode());
			System.out.println("Result of 'testAddressBook': " + result6.getResponse().trim());

			EnvelopeVersion stored = node
					.fetchEnvelope(testService.getServiceNameVersion().getName() + "$" + "addressbook");
			ContactContainer cc = (ContactContainer) stored.getContent();
			cc.addContact("1337");
			EnvelopeVersion env = node.createUnencryptedEnvelope(stored, cc);
			node.storeEnvelope(env, testService);

			c.setLogin(agentAdam.getIdentifier(), passAdam);
			result6 = c.sendRequest("GET", mainPath + "addressbook", "");
			assertEquals(200, result6.getHttpCode());
			System.out.println("Result of 'testAddressBook2': " + result6.getResponse().trim());
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
	}

	@Test
	public void testGetAddressBookWithoutArtifact() {
		MiniClient c = new MiniClient();
		c.setAddressPort(HTTP_ADDRESS, HTTP_PORT);

		try {
			c.setLogin(agentAdam.getIdentifier(), passAdam);
			ClientResponse result = c.sendRequest("GET", mainPath + "addressbook", "");
			assertEquals(200, result.getHttpCode());
			System.out.println("Result of 'testAddressBook': " + result.getResponse().trim());

			ClientResponse result2 = c.sendRequest("GET", mainPath + "groups", "");
			assertEquals(200, result2.getHttpCode());
			System.out.println("Result of 'testAddressBook': " + result2.getResponse().trim());
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
	}

	@Test
	public void testRMI() {
		MiniClient c = new MiniClient();
		c.setAddressPort(HTTP_ADDRESS, HTTP_PORT);

		try {
			c.setLogin(agentAdam.getIdentifier(), passAdam);

			// Get permissions
			ClientResponse result = c.sendRequest("GET", mainPath + "permission", "");
			assertEquals(200, result.getHttpCode());
			System.out.println("Result of 'testRMI': " + result.getResponse().trim());

			// Set permissions
			ClientResponse result1 = c.sendRequest("POST", mainPath + "permission",
					"{firstName:" + "true" + ",lastName:" + "true" + ",userImage:" + "true" + "}");
			assertEquals(200, result1.getHttpCode());
			System.out.println("Result of 'testRMI': " + result1.getResponse().trim());

			// Set Profile Information
			ClientResponse result2 = c.sendRequest("POST", mainPath + "user",
					"{firstName:" + "\"Vorname\"" + ",lastName:" + "\"Nachname\"" + ",userImage:" + "\"Url\"" + "}");
			assertEquals(200, result2.getHttpCode());
			System.out.println("Result of 'testRMI': " + result2.getResponse().trim());

			ClientResponse result3 = c.sendRequest("GET", mainPath + "user", "");
			assertEquals(200, result3.getHttpCode());
			System.out.println("Result of 'testRMI': " + result3.getResponse().trim());

			ClientResponse result4 = c.sendRequest("GET", mainPath + "user/abel", "");
			assertEquals(200, result4.getHttpCode());
			System.out.println("Result of 'testRMI': " + result4.getResponse().trim());
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
	}

	@Test
	public void testRMIWithoutService() {
		MiniClient c = new MiniClient();
		c.setAddressPort(HTTP_ADDRESS, HTTP_PORT);
		try {
			node.unregisterReceiver(testService2);
			c.setLogin(agentAdam.getIdentifier(), passAdam);

			// Get permissions
			ClientResponse result = c.sendRequest("GET", mainPath + "permission", "");
			assertEquals(400, result.getHttpCode());
			System.out.println("Result of 'testRMI': " + result.getResponse().trim());

			// Set permissions
			ClientResponse result1 = c.sendRequest("POST", mainPath + "permission",
					"{firstName:" + "true" + ",lastName:" + "true" + ",userImage:" + "true" + "}");
			assertEquals(400, result1.getHttpCode());
			System.out.println("Result of 'testRMI': " + result1.getResponse().trim());

			// Set Profile Information
			ClientResponse result2 = c.sendRequest("POST", mainPath + "user",
					"{firstName:" + "" + ",lastName:" + "" + ",userImage:" + "" + "}");
			assertEquals(400, result2.getHttpCode());
			System.out.println("Result of 'testRMI': " + result2.getResponse().trim());

			ClientResponse result3 = c.sendRequest("GET", mainPath + "user", "");
			assertEquals(400, result3.getHttpCode());
			System.out.println("Result of 'testRMI': " + result3.getResponse().trim());

			ClientResponse result4 = c.sendRequest("GET", mainPath + "user/abel", "");
			assertEquals(400, result4.getHttpCode());
			System.out.println("Result of 'testRMI': " + result4.getResponse().trim());
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
	}

	@Test
	public void testBlockEnvelopes() {
		MiniClient c = new MiniClient();
		c.setAddressPort(HTTP_ADDRESS, HTTP_PORT);

		try {
			agentEve.unlock(passEve);
			// Blocking contacts
			createEnvelope("contacts_" + agentAdam.getIdentifier(), agentEve);

			c.setLogin(agentAdam.getIdentifier(), passAdam);
			// get Contacts with no contacts
			ClientResponse result = c.sendRequest("GET", mainPath, "", "text/plain", "application/json",
					new HashMap<String, String>());
			assertEquals(400, result.getHttpCode());
			System.out.println("Result of 'testBlockEnvelopes': " + result.getResponse().trim());

			result = c.sendRequest("POST", mainPath + "eve1st", "");
			assertEquals(400, result.getHttpCode());
			System.out.println("Result of 'testBlockEnvelopes': " + result.getResponse().trim());

			result = c.sendRequest("DELETE", mainPath + "eve1st", "");
			assertEquals(400, result.getHttpCode());
			System.out.println("Result of 'testBlockEnvelopes': " + result.getResponse().trim());

			// Blocking groups
			createEnvelope("groups_", agentEve);
			createEnvelope("groups_test", agentEve);
			c.setLogin(agentAdam.getIdentifier(), passAdam);
			ClientResponse result2 = c.sendRequest("GET", mainPath + "groups", "", "text/plain", "application/json",
					new HashMap<String, String>());
			assertEquals(400, result2.getHttpCode());
			System.out.println("Result of 'testBlockEnvelopes': " + result2.getResponse().trim());

			result2 = c.sendRequest("POST", mainPath + "groups/testGroup/member/abel", "");
			assertEquals(400, result2.getHttpCode());
			System.out.println("Result of 'testBlockEnvelopes': " + result2.getResponse().trim());

			result2 = c.sendRequest("DELETE", mainPath + "groups/testGroup/member/abel", "");
			assertEquals(400, result2.getHttpCode());
			System.out.println("Result of 'testBlockEnvelopes': " + result2.getResponse().trim());

			// Blocking address book
			createEnvelope("addressbook", agentEve);
			c.setLogin(agentAdam.getIdentifier(), passAdam);

			ClientResponse result3 = c.sendRequest("POST", mainPath + "addressbook", "");
			assertEquals(400, result3.getHttpCode());
			System.out.println("Result of 'testBlockEnvelopes': " + result3.getResponse().trim());

			result3 = c.sendRequest("DELETE", mainPath + "addressbook", "");
			assertEquals(400, result3.getHttpCode());
			System.out.println("Result of 'testBlockEnvelopes': " + result3.getResponse().trim());

			result3 = c.sendRequest("GET", mainPath + "addressbook", "");
			assertEquals(400, result3.getHttpCode());
			System.out.println("Result of 'testBlockEnvelopes': " + result3.getResponse().trim());

		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
	}

	@Test
	public void testRemoveWithoutStorage() {
		MiniClient c = new MiniClient();
		c.setAddressPort(HTTP_ADDRESS, HTTP_PORT);

		try {
			c.setLogin(agentAdam.getIdentifier(), passAdam);

			// Remove contact from addressbook
			ClientResponse result1 = c.sendRequest("DELETE", mainPath + "addressbook", "");
			assertEquals(404, result1.getHttpCode());
			System.out.println("Result of 'testAddressBook': " + result1.getResponse().trim());

			// Remove Contact
			ClientResponse result2 = c.sendRequest("DELETE", mainPath + "eve1st", "");
			assertEquals(404, result2.getHttpCode());
			System.out.println("Result of 'testAddRemoveContact': " + result2.getResponse().trim());

			// Check groups
			ClientResponse result4 = c.sendRequest("DELETE", mainPath + "groups/testGroup", "");
			assertEquals(400, result4.getHttpCode());
			System.out.println("Result of 'testGroups': " + result4.getResponse().trim());

		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
	}

	// helper method
	public void createEnvelope(String identifier, AgentImpl owner) {
		ContactContainer cc = new ContactContainer();
		try {
			EnvelopeVersion env = node.createEnvelope(testService.getServiceNameVersion().getName() + "$" + identifier,
					owner.getPublicKey(), cc, owner);
			node.storeEnvelope(env, owner);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			fail("Could not create Envelope.\n" + e.getMessage());
			e.printStackTrace();
		}
	}

	// helper method
	public void createEnvelopeWithContent(String identifier, AgentImpl owner, ContactContainer cc) {
		try {
			EnvelopeVersion env = node.createEnvelope(testService.getServiceNameVersion().getName() + "$" + identifier,
					owner.getPublicKey(), cc, owner);
			node.storeEnvelope(env, owner);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			fail("Could not create Envelope.\n" + e.getMessage());
			e.printStackTrace();
		}
	}
}
