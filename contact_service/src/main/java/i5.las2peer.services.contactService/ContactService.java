package i5.las2peer.services.contactService;

import java.io.Serializable;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import i5.las2peer.api.Context;
import i5.las2peer.api.ManualDeployment;
import i5.las2peer.api.persistency.Envelope;
import i5.las2peer.api.persistency.EnvelopeException;
import i5.las2peer.api.persistency.EnvelopeNotFoundException;
import i5.las2peer.api.security.Agent;
import i5.las2peer.api.security.AgentException;
import i5.las2peer.api.security.AgentNotFoundException;
import i5.las2peer.api.security.GroupAgent;
import i5.las2peer.api.security.UserAgent;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.restMapper.RESTService;
import i5.las2peer.restMapper.annotations.ServicePath;
import i5.las2peer.security.BotAgent;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Contact;
import io.swagger.annotations.Info;
import io.swagger.annotations.License;
import io.swagger.annotations.SwaggerDefinition;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;

/**
 * las2peer Contact Service
 * 
 * This service can manage your las2peer contacts and groups. It uses the
 * las2peer Web-Connector for RESTful access to it. The service has the
 * following features:
 * 
 * <p>
 * ContactResource:
 * <ul>
 * <li>Gets all your contacts
 * <li>Adds/Removes a contact
 * </ul>
 * <p>
 * GroupResource:
 * <ul>
 * <li>Gets all your groups
 * <li>Adds/Removes a group
 * <li>Gets all member of a group
 * <li>Adds a user to a group
 * <li>Removes a user from a group
 * </ul>
 * <p>
 * AddressBookResource:
 * <ul>
 * <li>Adds you to the address book
 * <li>Removes you from the address book
 * <li>Gets all users from the address book
 * </ul>
 * UserResource:
 * <ul>
 * <li>Updates your user information
 * <li>Gets your or a users user information
 * </ul>
 * PermissionResource:
 * <ul>
 * <li>Gets a user's permission settings
 * <li>Sets your permission settings
 * </ul>
 * 
 * 
 * @author Alexander Neumann
 * @version 0.2.4
 */
@ServicePath("contactservice")
@ManualDeployment
public class ContactService extends RESTService {

	private static final String USER_INFORMATION_SERVICE = "i5.las2peer.services.userInformationService.UserInformationService@0.2.5";

	// instantiate the logger class
	private final static L2pLogger logger = L2pLogger.getInstance(ContactService.class.getName());
	private final static String contact_prefix = "contacts";
	private final static String group_prefix = "groups";
	private final static String address_prefix = "addressbook";
	private String contactStorerAgentName;
	private String contactStorerAgentPW;
	private static String contactStorerAgentNameStatic;
	private static String contactStorerAgentPWStatic;

	@Override
	protected void initResources() {
		getResourceConfig().register(ContactResource.class);
		getResourceConfig().register(GroupResource.class);
		getResourceConfig().register(AddressBookResource.class);
		getResourceConfig().register(UserResource.class);
		getResourceConfig().register(PermissionResource.class);
		getResourceConfig().register(NameResource.class);
		setFieldValues();
		contactStorerAgentNameStatic = contactStorerAgentName;
		contactStorerAgentPWStatic = contactStorerAgentPW;
	}

	// //////////////////////////////////////////////////////////////////////////////////////
	// Service methods.
	// //////////////////////////////////////////////////////////////////////////////////////
	@Path("/") // this is the root resource
	@Api(value = "Contact Resource")
	@SwaggerDefinition(info = @Info(title = "laspeer Contact Service", version = "0.2.4", description = "A las2peer Contact Service for managing your contacts and groups.", termsOfService = "", contact = @Contact(name = "Alexander Neumann", url = "https://github.com/rwth-acis/las2peer-Contact-Service", email = "neumann@dbis.rwth-aachen.de"), license = @License(name = "ACIS License (BSD3)", url = "https://github.com/rwth-acis/las2peer-Contact-Service/blob/master/LICENSE")))
	public static class ContactResource {
		ContactService service = (ContactService) Context.get().getService();

		/**
		 * Get all your contacts from the storage.
		 * 
		 * @return Returns a JSON string with a list of your contacts { id:name }.
		 * @since 0.1
		 */
		@GET
		@Produces(MediaType.APPLICATION_JSON)
		@ApiOperation(value = "Get Contacts", notes = "Get all your contacts.")
		@ApiResponses(value = {
				@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Got a list of your contacts."),
				@ApiResponse(code = HttpURLConnection.HTTP_BAD_REQUEST, message = "Storage problems.") })
		public Response getContacts() {
			Agent owner = Context.get().getMainAgent();
			String identifier = contact_prefix + "_" + owner.getIdentifier();
			JSONObject result = new JSONObject();
			try {
				try {
					Envelope stored = Context.get().requestEnvelope(identifier, owner);
					ContactContainer cc = (ContactContainer) stored.getContent();
					HashSet<String> userList = cc.getUserList();
					UserAgent user;
					for (String l : userList) {
						try {
							user = (UserAgent) Context.get().fetchAgent(l);
							result.put(user.getIdentifier(), user.getLoginName());
						} catch (AgentNotFoundException e1) {
							// Skip unknown agents.
						}
					}
				} catch (EnvelopeNotFoundException e) {
					ContactContainer cc = new ContactContainer();
					Envelope env = Context.get().createEnvelope(identifier, owner);
					env.setContent(cc);
					Context.get().storeEnvelope(env, owner);
				}
			} catch (Exception e) {
				// write error to logfile and console
				logger.log(Level.SEVERE, "Unknown Error occured!", e);
				// create and publish a monitoring message
				return Response.status(Status.BAD_REQUEST).entity(e.toString()).build();
			}
			return Response.status(Status.OK).entity(result).build();
		}

		/**
		 * Adds a contact to your list. Information is stored in an envelope which holds
		 * all your contacts.
		 * 
		 * @param name Login name of the contact you want to add
		 * @return Returns a Response
		 * @since 0.1
		 */
		@POST
		@Path("{name}")
		@Produces(MediaType.TEXT_PLAIN)
		@ApiOperation(value = "Add Contact", notes = "Add a contact to your contact list.")
		@ApiResponses(value = { @ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Contact added."),
				@ApiResponse(code = HttpURLConnection.HTTP_BAD_REQUEST, message = "Contact already in list or storage problems."),
				@ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "Agent does not exist.") })
		public Response addContact(@PathParam("name") String name) {
			// Setting owner and identifier for envelope
			Agent owner = Context.get().getMainAgent();
			String identifier = contact_prefix + "_" + owner.getIdentifier();
			Envelope env = null;
			boolean added = false;
			String userID = "";

			// try to fetch user you want to add
			try {
				userID = Context.get().getUserAgentIdentifierByLoginName(name);
			} catch (AgentException ex) {
				return Response.status(Status.NOT_FOUND).entity("Agent does not exist.").build();
			}

			// try to get envelope
			ContactContainer cc = null;
			try {
				try {
					env = Context.get().requestEnvelope(identifier);
					cc = (ContactContainer) env.getContent();
				} catch (EnvelopeNotFoundException e) {
					cc = new ContactContainer();
					env = Context.get().createEnvelope(identifier);
				}
			} catch (Exception e) {
				// write error to logfile and console
				logger.log(Level.SEVERE, "Unknown error occured!", e);
				return Response.status(Status.BAD_REQUEST).build();
			}
			added = cc.addContact(userID);
			env.setContent(cc);
			// try to store envelope
			service.storeEnvelope(env, owner);

			if (added) {
				return Response.status(Status.OK).entity("Contact added.").build();
			} else {
				return Response.status(Status.BAD_REQUEST).entity("Contact already in list.").build();
			}
		}

		/**
		 * Removes a contact from your list.
		 * 
		 * @param name Login name of the contact you want to delete
		 * @return Returns a Response whether the contact was deleted or not.
		 * @since 0.1
		 */
		@DELETE
		@Path("{name}")
		@Produces(MediaType.TEXT_PLAIN)
		@ApiResponses(value = { @ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Contact removed."),
				@ApiResponse(code = HttpURLConnection.HTTP_BAD_REQUEST, message = "Contact not in list or storage problems."),
				@ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "Agent does not exist.") })
		@ApiOperation(value = "Remove Contact", notes = "Removes a contact from your contact list.")
		public Response removeContact(@PathParam("name") String name) {
			Agent owner = Context.get().getMainAgent();
			String identifier = contact_prefix + "_" + owner.getIdentifier();
			Envelope env = null;
			boolean deleted = false;
			ContactContainer cc = null;
			try {
				try {
					env = Context.get().requestEnvelope(identifier, owner);
					cc = (ContactContainer) env.getContent();
					String userID = Context.get().getUserAgentIdentifierByLoginName(name);
					deleted = cc.removeContact(userID);
				} catch (EnvelopeNotFoundException e) {
					cc = new ContactContainer();
					env = Context.get().createEnvelope(identifier, owner);
				} catch (AgentException ex) {
					return Response.status(Status.NOT_FOUND).entity("Agent does not exist").build();
				}
			} catch (Exception e) {
				// write error to logfile and console
				logger.log(Level.SEVERE, "Can't persist to network storage!", e);
				// create and publish a monitoring message
				return Response.status(Status.BAD_REQUEST).entity("Could not delete Contact").build();
			}
			env.setContent(cc);
			service.storeEnvelope(env, owner);
			if (deleted) {
				return Response.status(Status.OK).entity("Contact removed.").build();
			} else {
				return Response.status(Status.NOT_FOUND).entity("User is not one of your contacts.").build();
			}
		}
	}

	@Path("/groups") // this is the root resource
	@Api(value = "Group Resource")
	@SwaggerDefinition(info = @Info(title = "laspeer Contact Service", version = "0.1", description = "A las2peer Contact Service for managing your contacts and groups.", termsOfService = "", contact = @Contact(name = "Alexander Neumann", url = "https://github.com/rwth-acis/las2peer-Contact-Service", email = "neumann@dbis.rwth-aachen.de"), license = @License(name = "ACIS License (BSD3)", url = "https://github.com/rwth-acis/las2peer-Contact-Service/blob/master/LICENSE")))
	public static class GroupResource {
		ContactService service = (ContactService) Context.get().getService();

		/**
		 * Retrieve a list of all your groups.
		 * 
		 * @return Returns a Response containing a list of your groups
		 * @since 0.1
		 */
		@GET
		@Produces(MediaType.APPLICATION_JSON)
		@ApiOperation(value = "Get Groups", notes = "Get all your Groups.")
		@ApiResponses(value = { @ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Got a list of your groups."),
				@ApiResponse(code = HttpURLConnection.HTTP_BAD_REQUEST, message = "Storage problems.") })
		public Response getGroups() {
			String identifier = contactStorerAgentPWStatic;
			JSONObject result = new JSONObject();
			UserAgent contactStorer = null;
			try {
				try {
					contactStorer = (UserAgent) Context.getCurrent().fetchAgent(
							Context.getCurrent().getUserAgentIdentifierByLoginName(contactStorerAgentNameStatic));
					contactStorer.unlock(contactStorerAgentPWStatic);
				} catch (Exception e) {
					System.out.println("apparently no contact storer there or not unlockable");
				}
				try {
					Envelope stored = Context.get().requestEnvelope(identifier, contactStorer);
					ContactContainer cc = (ContactContainer) stored.getContent();
					Set<String> groupNames = cc.getGroups().keySet();
					String groupId = "";
					for (String s : groupNames) {
						try {
							groupId = cc.getGroupId(s);
							Context.get().requestAgent(groupId);
							result.put(groupId, s);
						} catch (Exception e) {
							// Skip agents who are not known or groups wihtout access.
						}
					}
					return Response.status(Status.OK).entity(result).build();
				} catch (EnvelopeNotFoundException e) {
					ContactContainer cc = new ContactContainer();
					Envelope env = null;
					env = Context.get().createEnvelope(identifier);
					env.setPublic();
					env.setContent(cc);
					service.storeEnvelope(env, contactStorer);
					return Response.status(Status.OK).entity(result).build();
				}
			} catch (Exception e) {
				// write error to logfile and console
				logger.log(Level.SEVERE, "Can't persist to network storage!", e);
			}
			return Response.status(Status.BAD_REQUEST).entity("Unknown error occured.").build();
		}

		/**
		 * Get group information via name.
		 * 
		 * @param name Name of your group
		 * @return Returns a Response
		 * @since 0.1
		 * 
		 */
		@GET
		@Path("/{name}")
		@Produces(MediaType.APPLICATION_JSON)
		@ApiOperation(value = "Get Group from Name", notes = "Get a group via name.")
		@ApiResponses(value = { @ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Group found."),
				@ApiResponse(code = HttpURLConnection.HTTP_BAD_REQUEST, message = "Group not found or storage problems.") })
		public Response getGroup(@PathParam("name") String name) {
			String identifier = contactStorerAgentPWStatic + "_" + name;
			try {
				Envelope stored = Context.get().requestEnvelope(identifier);
				ContactContainer cc = (ContactContainer) stored.getContent();
				String id = cc.getGroupId(name);
				if (id == null) {
					return Response.status(Status.NOT_FOUND).entity("Group not found").build();
				}
				JSONObject result = new JSONObject();
				result.put(id, name);
				return Response.status(Status.OK).entity(result).build();
			} catch (Exception e) {
				// write error to logfile and console
				logger.log(Level.SEVERE, "Can't persist to network storage!", e);
				// create and publish a monitoring message
				e.printStackTrace();
			}
			return Response.status(Status.BAD_REQUEST).entity("{}").build();
		}

		/**
		 * Adds a group. Creates a group agent and stores the name in an enevelope. The
		 * evnelope makes it possible to request the Agent again and an extra envelope
		 * encrypted with the service accessible for all users.
		 * 
		 * @param name Name of your group
		 * @return Returns a Response whether the group could be added or not.
		 * @since 0.1
		 * 
		 */
		@POST
		@Path("/{name}")
		@Produces(MediaType.TEXT_PLAIN)
		@ApiResponses(value = { @ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Group created."),
				@ApiResponse(code = HttpURLConnection.HTTP_BAD_REQUEST, message = "Storage problems or group already exist.") })
		@ApiOperation(value = "Create Group", notes = "Creates a group")
		public Response addGroup(@PathParam("name") String name) {
			// Setting owner group members
			Agent[] members = new Agent[1];
			members[0] = Context.get().getMainAgent();
			Envelope env = null;
			Envelope env2 = null;
			String id = "";
			String identifier = contactStorerAgentPWStatic + "_" + name;
			String identifier2 = contactStorerAgentPWStatic;
			GroupAgent groupAgent;
			ContactContainer cc = null;
			UserAgent contactStorer = null;
			try {
				try {
					Context.get().requestEnvelope(identifier);
					return Response.status(Status.BAD_REQUEST).entity("Group already exist").build();
				} catch (EnvelopeNotFoundException e) {
					cc = new ContactContainer();
					// try to create group
					groupAgent = Context.get().createGroupAgent(members);
					id = groupAgent.getIdentifier();
					groupAgent.unlock(Context.get().getMainAgent());
					Context.get().storeAgent(groupAgent);
					cc.addGroup(name, id);
					env = Context.get().createEnvelope(identifier, groupAgent);
					env.setContent(cc);
					service.storeEnvelope(env, groupAgent);
				}
				// writing to user
				try {
					contactStorer = (UserAgent) Context.getCurrent().fetchAgent(
							Context.getCurrent().getUserAgentIdentifierByLoginName(contactStorerAgentNameStatic));
					contactStorer.unlock(contactStorerAgentPWStatic);
					try {
						// try to add group to group list
						env2 = Context.get().requestEnvelope(identifier2, contactStorer);
						cc = (ContactContainer) env2.getContent();
						System.out.println(cc);
					} catch (EnvelopeNotFoundException e) {
						// create new group list
						cc = new ContactContainer();
						env2 = Context.get().createEnvelope(identifier2, contactStorer);
						env2.setPublic();
					}
				} catch (Exception e) {
					System.out.println("apparently no contact storer there or not unlockable");
				}

			} catch (Exception e) {
				// write error to logfile and console
				logger.log(Level.SEVERE, "Can't persist to network storage!", e);
				e.printStackTrace();
				return Response.status(Status.BAD_REQUEST).entity("Error").build();
			}

			cc.addGroup(name, id);
			env2.setContent(cc);
			if (contactStorer != null) {
				service.storeEnvelope(env2, contactStorer);
			} else
				logger.log(Level.SEVERE, "Contactstorer is Null!", e);
			return Response.status(Status.OK).entity("" + id).build();
		}

		/**
		 * Removes a group.
		 * 
		 * @param name Name of the group you want to delete.
		 * @return Returns a Response whether the group could be deleted or not.
		 * @since 0.1
		 */
		@DELETE
		@Path("/{name}")
		@Produces(MediaType.TEXT_PLAIN)
		@ApiResponses(value = { @ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Group removed."),
				@ApiResponse(code = HttpURLConnection.HTTP_BAD_REQUEST, message = "Group does not exist or storage problems.") })
		@ApiOperation(value = "Remove Group", notes = "Removes a group.")
		public Response removeGroup(@PathParam("name") String name) {
			Envelope env = null;
			try {
				String identifier = contactStorerAgentPWStatic + "_" + name;
				env = Context.get().requestEnvelope(identifier);
				ContactContainer cc = (ContactContainer) env.getContent();
				String groupID = cc.getGroups().get(name);
				if (groupID == null) {
					return Response.status(Status.NOT_FOUND).entity("Group not found").build();
				}
				cc.removeGroup(name);
				env.setContent(cc);
				GroupAgent ga = (GroupAgent) Context.get().requestAgent(groupID);
				ga.revokeMember(Context.get().getMainAgent());
				Context.get().storeAgent(ga);
				Context.get().storeEnvelope(env);
			} catch (Exception e) {
				// write error to logfile and console
				logger.log(Level.SEVERE, "Can't persist to network storage!", e);
				return Response.status(Status.BAD_REQUEST).entity("Error").build();
			}
			return Response.status(Status.OK).build();
		}

		/**
		 * Retrieve all members of a group.
		 * 
		 * @param name Name of the group.
		 * @return Returns a Response with the list of all members.
		 * @since 0.1
		 */
		@GET
		@Path("/{name}/member")
		@Produces(MediaType.APPLICATION_JSON)
		@ApiOperation(value = "Get Group Member", notes = "Get all members of your group.")
		@ApiResponses(value = { @ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Got all members of a group"),
				@ApiResponse(code = HttpURLConnection.HTTP_BAD_REQUEST, message = "Storage problems.") })
		public Response getGroupMember(@PathParam("name") String name) {
			JSONObject result = new JSONObject();
			String identifier = contactStorerAgentPWStatic + "_" + name;
			try {
				Envelope stored = Context.get().requestEnvelope(identifier);
				ContactContainer cc = (ContactContainer) stored.getContent();
				GroupAgent groupAgent = (GroupAgent) Context.get()
						.requestAgent(String.valueOf(cc.getGroups().get(name)));
				groupAgent.unlock(Context.get().getMainAgent());
				String[] memberIds = groupAgent.getMemberList();
				for (String memberId : memberIds) {
					UserAgent user = (UserAgent) Context.get().fetchAgent(memberId);
					result.put(memberId, user.getLoginName());
				}
			} catch (Exception e) {
				// write error to logfile and console
				logger.log(Level.SEVERE, "Can't get member names!", e);
				return Response.status(Status.BAD_REQUEST).entity(e.toString()).build();
			}
			return Response.status(Status.OK).entity(result).build();
		}

		/**
		 * Retrieve id number of a group.
		 * 
		 * @param name Name of the group.
		 * @return Returns a Response with the group number.
		 * @since 0.2.1
		 */
		@GET
		@Path("/{name}/id")
		@Produces(MediaType.APPLICATION_JSON)
		@ApiOperation(value = "Get Group Id", notes = "Get the Id of the given group.")
		@ApiResponses(value = { @ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Got group id!"),
				@ApiResponse(code = HttpURLConnection.HTTP_BAD_REQUEST, message = "Storage problems.") })
		public Response getGroupId(@PathParam("name") String name) {
			JSONObject result = new JSONObject();
			String identifier = group_prefix + "_" + name;
			try {
				Envelope stored = Context.get().requestEnvelope(identifier);
				ContactContainer cc = (ContactContainer) stored.getContent();
				GroupAgent groupAgent = (GroupAgent) Context.get()
						.requestAgent(String.valueOf(cc.getGroups().get(name)));
				groupAgent.unlock(Context.get().getMainAgent());
				System.out.println(groupAgent);
				System.out.println(groupAgent.getIdentifier());
				result.put("groupId", groupAgent.getIdentifier());

			} catch (Exception e) {
				// write error to logfile and console
				logger.log(Level.SEVERE, "Can't get group id!", e);
				return Response.status(Status.BAD_REQUEST).entity(e.toString()).build();
			}
			return Response.status(Status.OK).entity(result).build();
		}

		/**
		 * Adds a member to a group.
		 * 
		 * @param groupName Name of the group.
		 * @param userName  Name of the user.
		 * @return Returns a Response
		 * @since 0.1
		 */
		@POST
		@Path("/{name}/member/{user}")
		@Produces(MediaType.APPLICATION_JSON)
		@ApiResponses(value = { @ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Groupmember added."),
				@ApiResponse(code = HttpURLConnection.HTTP_BAD_REQUEST, message = "Storage problems."),
				@ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "Agent does not exist.") })
		@ApiOperation(value = "Add Group Member", notes = "Add a member to a group.")
		public Response addGroupMember(@PathParam("name") String groupName, @PathParam("user") String userName) {
			Envelope env = null;
			String addID = "-1";
			Agent test = null;
			GroupAgent groupAgent = null;
			try {
				String identifier = contactStorerAgentPWStatic + "_" + groupName;
				// Get envelope
				env = Context.get().requestEnvelope(identifier, Context.get().getMainAgent());
				ContactContainer cc = (ContactContainer) env.getContent();
				groupAgent = (GroupAgent) Context.get().requestAgent(cc.getGroups().get(groupName));
				addID = Context.get().getUserAgentIdentifierByLoginName(userName);
				test = Context.get().fetchAgent(addID);
				groupAgent.addMember(test);
				Context.get().storeAgent(groupAgent);
				env.setContent(cc);
			} catch (AgentException e1) {
				return Response.status(Status.NOT_FOUND).entity("Agent not found.").build();
			} catch (Exception e) {
				// write error to logfile and console
				logger.log(Level.SEVERE, "Can't add member!", e);
				return Response.status(Status.BAD_REQUEST).entity("Error").build();
			}
			service.storeEnvelope(env, groupAgent);
			return Response.status(Status.OK).entity("Added to group.").build();
		}

		/**
		 * Removes a member of a group
		 * 
		 * @param groupName Name of the group
		 * @param userName  Name of the user
		 * @return Returns a Response
		 * @since 0.1
		 */
		@DELETE
		@Path("/{name}/member/{user}")
		@Produces(MediaType.APPLICATION_JSON)
		@ApiResponses(value = { @ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Groupmember removed."),
				@ApiResponse(code = HttpURLConnection.HTTP_BAD_REQUEST, message = "Storage problems."),
				@ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "Agent does not exist.") })
		@ApiOperation(value = "Remove Group Member", notes = "Removes a member from a group.")
		public Response removeGroupMember(@PathParam("name") String groupName, @PathParam("user") String userName) {
			Envelope env = null;
			GroupAgent groupAgent = null;
			try {
				String identifier = group_prefix + "_" + groupName;
				env = Context.get().requestEnvelope(identifier);
				ContactContainer cc = (ContactContainer) env.getContent();
				try {
					groupAgent = (GroupAgent) Context.get().requestAgent(cc.getGroups().get(groupName));
				} catch (AgentException e) {
					// Agent not found?
					e.printStackTrace();
					return Response.status(Status.NOT_FOUND).entity("GroupAgent not found.").build();
				}
				String addID = Context.get().getUserAgentIdentifierByLoginName(userName);
				groupAgent.revokeMember(Context.get().fetchAgent(addID));
				service.storeEnvelope(env, groupAgent);

				Context.get().storeAgent(groupAgent);
			} catch (Exception e) {
				// write error to logfile and console
				logger.log(Level.SEVERE, "Can't remove member!", e);
				return Response.status(Status.BAD_REQUEST).entity("Error").build();
			}
			return Response.status(Status.OK).entity("Removed from group.").build();
		}
	}

	@Path("/addressbook") // this is the root resource
	@Api(value = "Address Book Resource")
	@SwaggerDefinition(info = @Info(title = "laspeer Contact Service", version = "0.1", description = "A las2peer Contact Service for managing your contacts and groups.", termsOfService = "", contact = @Contact(name = "Alexander Neumann", url = "https://github.com/rwth-acis/las2peer-Contact-Service", email = "neumann@dbis.rwth-aachen.de"), license = @License(name = "ACIS License (BSD3)", url = "https://github.com/rwth-acis/las2peer-Contact-Service/blob/master/LICENSE")))
	public static class AddressBookResource {
		ContactService service = (ContactService) Context.get().getService();

		/**
		 * Function to add yourself to the address book.
		 * 
		 * @return Returns the result of the request.
		 * @since 0.1
		 */
		@POST
		@Produces(MediaType.TEXT_PLAIN)
		@ApiOperation(value = "Add to Address Book", notes = "Add yourself to the address book.")
		@ApiResponses(value = { @ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Added"),
				@ApiResponse(code = HttpURLConnection.HTTP_BAD_REQUEST, message = "Storage problems or already in list.") })
		public Response addToAddressBook() {
			Agent owner = Context.get().getMainAgent();
			String identifier = address_prefix;
			Envelope env = null;
			boolean added = false;
			ContactContainer cc = null;
			try {
				try {
					env = Context.get().requestEnvelope(identifier, Context.get().getServiceAgent());
					cc = (ContactContainer) env.getContent();
					added = cc.addContact(owner.getIdentifier());
				} catch (EnvelopeNotFoundException ex) {
					cc = new ContactContainer();
					added = cc.addContact(owner.getIdentifier());
					env = Context.get().createEnvelope(identifier, Context.get().getServiceAgent());
					env.setPublic();
				}
			} catch (Exception e) {
				// write error to logfile and console
				logger.log(Level.SEVERE, "Can't persist to network storage!", e);
				// create and publish a monitoring message
				return Response.status(Status.BAD_REQUEST).entity("Error").build();
			}

			env.setContent(cc);
			service.storeEnvelope(env, Context.get().getServiceAgent());
			if (added) {
				return Response.status(Status.OK).entity("Added to addressbook.").build();
			} else {
				return Response.status(Status.BAD_REQUEST).entity("Already in list.").build();
			}
		}

		/**
		 * Function to remove yourself from the address book.
		 * 
		 * @return Returns the result of the request.
		 * @since 0.1
		 */
		@DELETE
		@Produces(MediaType.TEXT_PLAIN)
		@ApiOperation(value = "Remove from Address Book", notes = "Removes yourself from the address book.")
		@ApiResponses(value = { @ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Removed from address book."),
				@ApiResponse(code = HttpURLConnection.HTTP_BAD_REQUEST, message = "Storage problems or you were not in the list.") })
		public Response removeFromAddressBook() {
			String identifier = address_prefix;
			Envelope env = null;
			ContactContainer cc = null;
			boolean deleted = false;
			try {
				try {
					env = Context.get().requestEnvelope(identifier, Context.get().getServiceAgent());
					cc = (ContactContainer) env.getContent();
					String userID = Context.get().getMainAgent().getIdentifier();
					deleted = cc.removeContact(userID);
				} catch (EnvelopeNotFoundException ex) {
					cc = new ContactContainer();
					env = Context.get().createEnvelope(identifier, Context.get().getServiceAgent());
					env.setPublic();
				}
			} catch (Exception e) {
				// write error to logfile and console
				logger.log(Level.SEVERE, "Can't persist to network storage!", e);
				// create and publish a monitoring message
				return Response.status(Status.BAD_REQUEST).entity("Could not be removed from list.").build();
			}

			env.setContent(cc);
			service.storeEnvelope(env, Context.get().getServiceAgent());
			if (deleted) {
				return Response.status(Status.OK).entity("Removed from list.").build();
			} else {
				return Response.status(Status.NOT_FOUND).entity("You were not in the list.").build();
			}
		}

		/**
		 * Function to get the address book.
		 * 
		 * @return Returns a JSON string containing users (id:name).
		 * @since 0.1
		 */
		@GET
		@Produces(MediaType.APPLICATION_JSON)
		@ApiOperation(value = "Get Address Book", notes = "Get all contacts from the address book.")
		@ApiResponses(value = { @ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Contacts received."),
				@ApiResponse(code = HttpURLConnection.HTTP_BAD_REQUEST, message = "Storage problems.") })
		public Response getAddressBook() {
			String identifier = address_prefix;
			JSONObject result = new JSONObject();
			try {
				try {
					Envelope stored = Context.get().requestEnvelope(identifier, Context.get().getServiceAgent());
					ContactContainer cc = (ContactContainer) stored.getContent();
					HashSet<String> list = cc.getUserList();
					UserAgent user;
					for (String l : list) {
						try {
							user = (UserAgent) Context.get().fetchAgent(l);
							result.put(user.getIdentifier(), user.getLoginName());
						} catch (AgentException | ClassCastException e1) {
							// Skip unknown agents
							e1.printStackTrace();
						}
					}
					return Response.status(Status.OK).entity(result).build();
				} catch (EnvelopeNotFoundException ex) {
					Envelope env = null;
					ContactContainer cc = new ContactContainer();
					env = Context.get().createEnvelope(identifier, Context.get().getServiceAgent());
					env.setPublic();
					env.setContent(cc);
					service.storeEnvelope(env, Context.get().getServiceAgent());
					return Response.status(Status.OK).entity(result).build();
				}
			} catch (Exception e) {
				// write error to logfile and console
				logger.log(Level.SEVERE, "Can't persist to network storage!", e);
				// create and publish a monitoring message
			}
			return Response.status(Status.BAD_REQUEST).entity("Could not get any contacts.").build();
		}
	}

	@Path("/user") // this is the root resource
	@Api(value = "User Resource")
	@SwaggerDefinition(info = @Info(title = "laspeer Contact Service", version = "0.1", description = "A las2peer Contact Service for managing your contacts and groups.", termsOfService = "", contact = @Contact(name = "Alexander Neumann", url = "https://github.com/rwth-acis/las2peer-Contact-Service", email = "neumann@dbis.rwth-aachen.de"), license = @License(name = "ACIS License (BSD3)", url = "https://github.com/rwth-acis/las2peer-Contact-Service/blob/master/LICENSE")))
	public static class UserResource {
		ContactService service = (ContactService) Context.get().getService();

		/**
		 * Function to set your information.
		 * 
		 * @param content A JSON string containing firstName, lastName and the
		 *                userImage.
		 * @return Response of the request.
		 * @since 0.1
		 */
		@POST
		@Produces(MediaType.APPLICATION_JSON)
		@ApiResponses(value = { @ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Updated user information."),
				@ApiResponse(code = HttpURLConnection.HTTP_BAD_REQUEST, message = "RMI error or wrong json.") })
		@ApiOperation(value = "Update User Information", notes = "Updates the name and the userimage.")
		public Response updateUserInformationREST(String content) {
			try {
				JSONParser parser = new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE);
				JSONObject params = (JSONObject) parser.parse(content);
				HashMap<String, Serializable> m = new HashMap<>();
				m.put("firstName", (String) params.get("firstName"));
				m.put("lastName", (String) params.get("lastName"));
				m.put("userImage", (String) params.get("userImage"));
				// RMI call without parameters
				Object result = Context.get().invoke(USER_INFORMATION_SERVICE, "set", new Serializable[] { m });
				if (result == null) {
					return Response.status(Status.BAD_REQUEST).entity("Setting user information failed. No result.")
							.build();
				} else if (!(result instanceof Boolean)) {
					return Response.status(Status.BAD_REQUEST).entity("Setting user information failed. Wrong type.")
							.build();
				}
			} catch (Exception e) {
				logger.log(Level.SEVERE, "Can't update user information!", e);
				return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
			}
			return Response.status(Status.OK).build();
		}

		/**
		 * Function to get the information of a user.
		 * 
		 * @return Returns a JSON string containing firstName, lastName and the
		 *         userImage.
		 * @since 0.1
		 */
		@GET
		@Produces(MediaType.TEXT_PLAIN)
		@ApiOperation(value = "Get User Information", notes = "Returns the name and the user image.")
		@ApiResponses(value = { @ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Got user information."),
				@ApiResponse(code = HttpURLConnection.HTTP_BAD_REQUEST, message = "RMI error.") })
		public Response getUserInformation() {
			String returnString = "";
			try {
				// RMI call without parameters
				String[] fields = { "firstName", "lastName", "userImage" };
				Object result = Context.get().invoke(USER_INFORMATION_SERVICE, "get",
						new Serializable[] { Context.get().getMainAgent().getIdentifier(), fields });
				if (result == null) {
					return Response.status(Status.BAD_REQUEST).entity("Getting user information failed. No result.")
							.build();
				} else if (!(result instanceof HashMap<?, ?>)) {
					return Response.status(Status.BAD_REQUEST).entity("Getting user information failed. Wrong type.")
							.build();
				} else {
					@SuppressWarnings({ "unchecked" })
					HashMap<String, Serializable> hashMap = (HashMap<String, Serializable>) result;
					returnString = hashMap.toString();
				}
			} catch (Exception e) {
				logger.log(Level.SEVERE, "Can't get user information!", e);
				return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
			}
			return Response.status(Status.OK).entity(returnString).build();
		}

		/**
		 * Function to get the information of a user.
		 * 
		 * @param name The name of the requested user.
		 * @return Returns a JSON string containing firstName, lastName and the
		 *         userImage depending on the permission set to this values.
		 * @since 0.1
		 */
		@GET
		@Path("/{name}")
		@Produces(MediaType.TEXT_PLAIN)
		@ApiOperation(value = "Get User Information for Name", notes = "Returns the name and the user image for a given user.")
		@ApiResponses(value = { @ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Got user information"),
				@ApiResponse(code = HttpURLConnection.HTTP_BAD_REQUEST, message = "RMI error or user does not exist.") })
		public Response getUserInformation(@PathParam("name") String name) {
			String returnString = "";
			try {
				// RMI call without parameters
				String[] fields = { "firstName", "lastName", "userImage" };
				Object result = Context.get().invoke(USER_INFORMATION_SERVICE, "get",
						new Serializable[] { Context.get().getUserAgentIdentifierByLoginName(name), fields });
				if (result != null) {
					@SuppressWarnings({ "unchecked" })
					HashMap<String, Serializable> hashMap = (HashMap<String, Serializable>) result;
					returnString = hashMap.toString();
				} else {
					return Response.status(Status.BAD_REQUEST).entity("Getting user information failed").build();
				}
			} catch (Exception e) {
				logger.log(Level.SEVERE, "Can't get user information for name!", e);
				return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
			}
			return Response.status(Status.OK).entity(returnString).build();
		}
	}

	@Path("/permission") // this is the root resource
	@Api(value = "Permission Resource")
	@SwaggerDefinition(info = @Info(title = "laspeer Contact Service", version = "0.1", description = "A las2peer Contact Service for managing your contacts and groups.", termsOfService = "", contact = @Contact(name = "Alexander Neumann", url = "https://github.com/rwth-acis/las2peer-Contact-Service", email = "neumann@dbis.rwth-aachen.de"), license = @License(name = "ACIS License (BSD3)", url = "https://github.com/rwth-acis/las2peer-Contact-Service/blob/master/LICENSE")))
	public static class PermissionResource {
		ContactService service = (ContactService) Context.get().getService();

		/**
		 * Function to get the user's permission setting
		 * 
		 * @return Returns a JSON string containing boolean values for firstName,
		 *         lastName and the userImage.
		 * @since 0.1
		 */
		@GET
		@Produces(MediaType.TEXT_PLAIN)
		@ApiOperation(value = "Get User Permission", notes = "Returns a field of the user's permissions")
		@ApiResponses(value = { @ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Got user permission."),
				@ApiResponse(code = HttpURLConnection.HTTP_BAD_REQUEST, message = "RMI error.") })
		public Response getUserPermissions() {
			String returnString = "No Response";
			try {
				// RMI call
				String[] fields = { "firstName", "lastName", "userImage" };
				Object result = Context.get().invoke(USER_INFORMATION_SERVICE, "getPermissions",
						new Serializable[] { fields });
				if (result == null) {
					return Response.status(Status.BAD_REQUEST).entity("Getting permissions failed. No result.").build();
				} else if (!(result instanceof HashMap<?, ?>)) {
					return Response.status(Status.BAD_REQUEST).entity("Getting permissions failed. Wrong type.")
							.build();
				} else {
					@SuppressWarnings({ "unchecked" })
					HashMap<String, Boolean> hashMap = (HashMap<String, Boolean>) result;
					returnString = hashMap.toString();
				}
			} catch (Exception e) {
				logger.log(Level.SEVERE, "Can't get user permission!", e);
				return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
			}
			return Response.status(Status.OK).entity(returnString).build();
		}

		/**
		 * Updates the user's permission setting (firstName, lastName, userImage)
		 * 
		 * @param content JSON string containing boolean values for firstName, lastName
		 *                and the userImage.
		 * @return Response
		 * @since 0.1
		 */
		@POST
		@Produces(MediaType.APPLICATION_JSON)
		@ApiResponses(value = { @ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Updated permissions"),
				@ApiResponse(code = HttpURLConnection.HTTP_BAD_REQUEST, message = "RMI error or wrong json.") })
		@ApiOperation(value = "updateUserPermission", notes = "Updates the name and the userimage")
		public Response updateUserPermissionREST(String content) {
			try {
				JSONParser parser = new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE);
				JSONObject params = (JSONObject) parser.parse(content);
				HashMap<String, Boolean> m = new HashMap<>();
				m.put("firstName", (Boolean) params.get("firstName"));
				m.put("lastName", (Boolean) params.get("lastName"));
				m.put("userImage", (Boolean) params.get("userImage"));
				// RMI call without parameters
				Object result = Context.get().invoke(USER_INFORMATION_SERVICE, "setPermissions", m);
				if (result == null) {
					return Response.status(Status.BAD_REQUEST).entity("Setting permissions failed. No result.").build();
				} else if (!(result instanceof Boolean)) {
					return Response.status(Status.BAD_REQUEST).entity("Setting permissions failed. Wrong type").build();
				} else {
					logger.info("setting permission: " + (result));
				}
			} catch (Exception e) {
				logger.log(Level.SEVERE, "Can't update user permission!", e);
				return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
			}
			return Response.status(Status.OK).entity("").build();
		}
	}

	@Path("/name") // this is the root resource
	public static class NameResource {
		ContactService service = (ContactService) Context.get().getService();

		/**
		 * Function to get the login name of an agent
		 * 
		 * @param id The id of the agent
		 * @return The login name
		 */
		@GET
		@Path("/{id}")
		public Response getName(@PathParam("id") String id) {
			try {
				UserAgent user = (UserAgent) Context.get().fetchAgent(id);
				String name = user.getLoginName();
				return Response.status(Status.OK).entity(name).build();
			} catch (AgentException e) {
				String error = "Agent not found";
				return Response.status(Status.NOT_FOUND).entity(error).build();
			}
		}
	}

	// //////////////////////////////////////////////////////////////////////////////////////
	// RMI Calls
	// //////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Envelope helper method for storing an envelope.
	 * 
	 * @param env   Envelope.
	 * @param owner Agent who owns the envelope.
	 * @since 0.1
	 */
	private void storeEnvelope(Envelope env, Agent owner) {
		try {
			Context.get().storeEnvelope(env, owner);
		} catch (EnvelopeException e) {
			e.printStackTrace();
		}
	}
}
