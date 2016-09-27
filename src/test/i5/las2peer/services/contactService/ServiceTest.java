package i5.las2peer.services.contactService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import i5.las2peer.p2p.LocalNode;
import i5.las2peer.p2p.ServiceNameVersion;
import i5.las2peer.security.ServiceAgent;
import i5.las2peer.security.UserAgent;
import i5.las2peer.testing.MockAgentFactory;
import i5.las2peer.webConnector.WebConnector;
import i5.las2peer.webConnector.client.ClientResponse;
import i5.las2peer.webConnector.client.MiniClient;

/**
 * Example Test Class demonstrating a basic JUnit test structure.
 *
 */
public class ServiceTest {

	private static final String HTTP_ADDRESS = "http://127.0.0.1";
	private static final int HTTP_PORT = WebConnector.DEFAULT_HTTP_PORT;

	private static LocalNode node;
	private static WebConnector connector;
	private static ByteArrayOutputStream logStream;

	private static UserAgent agentAdam;
	private static UserAgent agentEve;
	private static UserAgent agentAbel;
	private static final String passAdam = "adamspass";
	private static final String passEve = "evespass";
	private static final String passAbel = "abelspass";

	private static final String mainPath = "contacts/";

	/**
	 * Called before the tests start.
	 * 
	 * Sets up the node and initializes connector and users that can be used throughout the tests.
	 * 
	 * @throws Exception
	 */
	@BeforeClass
	public static void startServer() throws Exception {

		// start node
		node = LocalNode.newNode();
		agentAdam = MockAgentFactory.getAdam();
		agentAdam.unlockPrivateKey(passAdam);
		agentEve = MockAgentFactory.getEve();
		agentEve.unlockPrivateKey(passEve);
		agentAbel = MockAgentFactory.getAbel();
		agentAbel.unlockPrivateKey(passAbel);
		node.storeAgent(agentAdam);
		node.storeAgent(agentEve);
		node.storeAgent(agentAbel);
		node.launch();

		// during testing, the specified service version does not matter
		ServiceAgent testService = ServiceAgent.createServiceAgent(
				ServiceNameVersion.fromString("i5.las2peer.services.contactService.ContactService@1.0"), "a pass");
		testService.unlockPrivateKey("a pass");

		node.registerReceiver(testService);

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
	@AfterClass
	public static void shutDownServer() throws Exception {

		connector.stop();
		node.shutDown();

		connector = null;
		node = null;

		LocalNode.reset();

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
			c.setLogin(Long.toString(agentAdam.getId()), passAdam);

			// Add a contact
			ClientResponse result = c.sendRequest("GET", mainPath + "contact/eve1st", "");
			assertEquals(200, result.getHttpCode());
			assertTrue(result.getResponse().trim().contains("Contact added"));
			System.out.println("Result of 'testAddRemoveContact': " + result.getResponse().trim());

			// Add the same contact again
			ClientResponse result2 = c.sendRequest("GET", mainPath + "contact/eve1st", "");
			assertEquals(400, result2.getHttpCode());
			assertTrue(result2.getResponse().trim().contains("Contact already in list"));
			System.out.println("Result of 'testAddRemoveContact': " + result2.getResponse().trim());

			// Add a contact that does not exist
			ClientResponse result3 = c.sendRequest("GET", mainPath + "contact/eve2nd", "");
			assertEquals(404, result3.getHttpCode());
			assertTrue(result3.getResponse().trim().contains("Agent does not exist"));
			System.out.println("Result of 'testAddRemoveContact': " + result3.getResponse().trim());

			// Remove Contact
			ClientResponse result4 = c.sendRequest("POST", mainPath + "contact/eve1st", "");
			assertEquals(200, result4.getHttpCode());
			assertTrue(result4.getResponse().trim().contains("Contact removed"));
			System.out.println("Result of 'testAddRemoveContact': " + result4.getResponse().trim());

			// Try to remove contact again
			ClientResponse result5 = c.sendRequest("POST", mainPath + "contact/eve1st", "");
			assertEquals(400, result5.getHttpCode());
			assertTrue(result5.getResponse().trim().contains("User is not one of your contacts."));
			System.out.println("Result of 'testAddRemoveContact': " + result5.getResponse().trim());

			// Remove user that does not exist
			ClientResponse result6 = c.sendRequest("POST", mainPath + "contact/eve2nd", "");
			assertEquals(400, result6.getHttpCode());
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
			c.setLogin(Long.toString(agentAdam.getId()), passAdam);

			// Add a contact
			ClientResponse result = c.sendRequest("GET", mainPath + "contacts", "");
			assertEquals(200, result.getHttpCode());
			assertTrue(result.getResponse().trim().contains("{\"users\":[]}"));
			System.out.println("Result of 'testGetContacts': " + result.getResponse().trim());

			// with one contact
			c.sendRequest("GET", mainPath + "contact/eve1st", "");
			ClientResponse result2 = c.sendRequest("GET", mainPath + "contacts", "");
			assertEquals(200, result2.getHttpCode());
			assertTrue(result2.getResponse().trim().contains("{\"users\":[\"eve1st\"]}"));
			System.out.println("Result of 'testGetContacts': " + result2.getResponse().trim());

			// with more than one contact
			c.sendRequest("GET", mainPath + "contact/abel", "");
			ClientResponse result3 = c.sendRequest("GET", mainPath + "contacts", "");
			assertEquals(200, result3.getHttpCode());
			assertTrue(result3.getResponse().trim().contains("eve1st"));
			assertTrue(result3.getResponse().trim().contains("abel"));
			System.out.println("Result of 'testGetContacts': " + result3.getResponse().trim());

			// Remove Contacts
			c.sendRequest("POST", mainPath + "contact/eve1st", "");
			c.sendRequest("POST", mainPath + "contact/abel", "");

			// Check if list is empty again
			ClientResponse result4 = c.sendRequest("GET", mainPath + "contacts", "");
			assertEquals(200, result4.getHttpCode());
			assertTrue(result4.getResponse().trim().contains("{\"users\":[]}"));
			System.out.println("Result of 'testGetContacts': " + result4.getResponse().trim());
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
			c.setLogin(Long.toString(agentAdam.getId()), passAdam);

			// Add a contact
			ClientResponse result = c.sendRequest("POST", mainPath + "group", "{\"name\":\"testGroup\"}");
			assertEquals(200, result.getHttpCode());
			System.out.println("Result of 'testGroups': " + result.getResponse().trim());

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
			c.setLogin(Long.toString(agentAdam.getId()), passAdam);

			// Add a contact
			ClientResponse result = c.sendRequest("POST", mainPath + "addressbook/add", "");
			assertEquals(200, result.getHttpCode());
			System.out.println("Result of 'testAddressBook': " + result.getResponse().trim());

			c.setLogin(Long.toString(agentEve.getId()), passEve);
			ClientResponse result2 = c.sendRequest("POST", mainPath + "addressbook/add", "");
			assertEquals(200, result2.getHttpCode());
			System.out.println("Result of 'testAddressBook': " + result2.getResponse().trim());

			c.setLogin(Long.toString(agentAdam.getId()), passAdam);

			// Get contacts
			ClientResponse result3 = c.sendRequest("GET", mainPath + "addressbook", "");
			assertEquals(200, result3.getHttpCode());
			System.out.println("Result of 'testAddressBook': " + result3.getResponse().trim());

			// Remove contact
			ClientResponse result4 = c.sendRequest("POST", mainPath + "addressbook/remove", "");
			assertEquals(200, result4.getHttpCode());
			System.out.println("Result of 'testAddressBook': " + result4.getResponse().trim());

			c.setLogin(Long.toString(agentEve.getId()), passEve);
			ClientResponse result5 = c.sendRequest("GET", mainPath + "addressbook", "");
			assertEquals(200, result5.getHttpCode());
			System.out.println("Result of 'testAddressBook': " + result5.getResponse().trim());

		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
	}

}
