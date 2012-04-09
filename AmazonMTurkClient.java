package com.cclo7;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.codec.binary.Base64;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class AmazonMTurkClient {
	
	private final String ACCESS_KEY;
	private final String SECRET_KEY;
	private final String REST_API_VERSION;
	private final String SERVICE_URL;
	private final String PREVIEW_URL;

	private static final String DEFAULT_REST_API_VERSION = "2011-10-01";
	private static final String MTURK_SERVICE_NAME = "AWSMechanicalTurkRequester";
	private static final String PRODUCTION_SERVICE_URL = "https://mechanicalturk.amazonaws.com/";
	private static final String SANDBOX_SERVICE_URL = "https://mechanicalturk.sandbox.amazonaws.com/";
	private static final String PRODUCTION_PREVIEW_URL = "https://www.mturk.com/mturk/preview?";
	private static final String SANDBOX_PREVIEW_URL = "https://workersandbox.mturk.com/mturk/preview?";
	
	//operation name
	private static final String APPROVE_ASSIGNMENT_OPERATION = "ApproveAssignment";
	private static final String CREATE_HIT_OPERATION = "CreateHIT";
	private static final String EXTEND_HIT_OPERATION = "ExtendHIT";
	private static final String GET_ASSIGNMENTS_FOR_HIT_OPERATION = "GetAssignmentsForHIT"; 
	private static final String GRANT_BONUS_OPERATION = "GrantBonus";
	private static final String REJECT_ASSIGNMENT_OPERATION = "RejectAssignment";
	private static final String SET_HITTYPE_NOTIFICATION_OPERATION = "SetHITTypeNotification"; 
	
	private static final int REST_REQUEST_RETRY_LIMIT = 15;
			
	public AmazonMTurkClient(String accessKey, String secretKey, String restApiVersion, boolean isUseSandbox){
		this.ACCESS_KEY = accessKey;
		this.SECRET_KEY = secretKey;
		this.REST_API_VERSION = restApiVersion;
		
		if(isUseSandbox){
			this.SERVICE_URL = SANDBOX_SERVICE_URL;
			this.PREVIEW_URL = SANDBOX_PREVIEW_URL;
		}else{
			this.SERVICE_URL = PRODUCTION_SERVICE_URL;
			this.PREVIEW_URL = PRODUCTION_PREVIEW_URL;
		}
	}
	
	public AmazonMTurkClient(String accessKey, String secretKey, boolean isUseSandbox){
		this(accessKey, secretKey, DEFAULT_REST_API_VERSION, isUseSandbox);
	}
	
	/*
	 * functions for MTurk operations
	 */
	public boolean approveAssignment(String assignmentId){
		
		Map<String, String> parameters = new HashMap<String, String>(1);
		parameters.put("AssignmentId", assignmentId);
		String response = this.makeMTurkRequest(APPROVE_ASSIGNMENT_OPERATION, parameters);
		
		if(response != null && response.length() > 0){
			String responseXML = this.decodeXML(response.toString());
			
			try{
				DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
				DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
				Document doc = dBuilder.parse(new InputSource(new StringReader(responseXML)));
				doc.getDocumentElement().normalize();
				
				NodeList isValidNodeList = doc.getElementsByTagName("IsValid");
				if(isValidNodeList.getLength() > 0){
					Element isValidElement = (Element) isValidNodeList.item(0);
					String isValid = isValidElement.getTextContent();
					
					if(isValid.equals("True")){
						return true;
					}
				}
				
				NodeList messageNodeList = doc.getElementsByTagName("Message");
				if(messageNodeList.getLength() > 0){
					Element messageElement = (Element) messageNodeList.item(0);
					String message = messageElement.getTextContent();
					
					if(message.contains("Submitted")){
						return true;
					}
				}
				
			} catch(ParserConfigurationException e) {
				e.printStackTrace();
			} catch (SAXException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		return false;
	}
	
	public Map<String, String> createHIT(String title, String description, String question, double rewardAmt,
			long maxAssignments, long assignmentDurationInSeconds, long lifetimeInSeconds,
			long autoApprovalDelayInSeconds, QualificationRequirement qualificationRequirement){
		Map<String, String> responseMap = new HashMap<String, String>(3);
		
		Map<String, String> parameters = new HashMap<String, String>(1);
		parameters.put("Title", title);
		parameters.put("Description", description);
		parameters.put("Question", question);
		parameters.put("Reward.1.Amount", Double.toString(rewardAmt));
		parameters.put("Reward.1.CurrencyCode", "USD");
		parameters.put("MaxAssignments", Long.toString(maxAssignments));
		parameters.put("AssignmentDurationInSeconds", Long.toString(assignmentDurationInSeconds));
		parameters.put("LifetimeInSeconds", Long.toString(lifetimeInSeconds));
		parameters.put("AutoApprovalDelayInSeconds", Long.toString(autoApprovalDelayInSeconds));
	
		if(qualificationRequirement != null){
			parameters.put("QualificationRequirement.1.QualificationTypeId", qualificationRequirement.getTypeId());
			parameters.put("QualificationRequirement.1.Comparator", qualificationRequirement.getComparator());
			parameters.put("QualificationRequirement.1.IntegerValue", Integer.toString(qualificationRequirement.getIntegerValue()));
		}
	
		String response = this.makeMTurkRequest(CREATE_HIT_OPERATION, parameters);

		if(response != null && response.length() > 0){
			String responseXML = this.decodeXML(response.toString());
	
			try{
				DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
				DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
				Document doc = dBuilder.parse(new InputSource(new StringReader(responseXML)));
				doc.getDocumentElement().normalize();
				
				NodeList isValidNodeList = doc.getElementsByTagName("IsValid");
				if(isValidNodeList.getLength() > 0){
					Element isValidElement = (Element) isValidNodeList.item(0);
					String isValid = isValidElement.getTextContent();
					
					if(isValid.equals("True")){
						
						String hitId = null;
						NodeList hitIdNodeList = doc.getElementsByTagName("HITId");
						if(hitIdNodeList.getLength() > 0){
							Element hitIdElement = (Element) hitIdNodeList.item(0);
							hitId = hitIdElement.getTextContent();
							responseMap.put("hitId", hitId);
						}
						
						String hitTypeId = null;
						NodeList hitTypeIdNodeList = doc.getElementsByTagName("HITTypeId");
						if(hitTypeIdNodeList.getLength() > 0){
							Element hitTypeIdElement = (Element) hitTypeIdNodeList.item(0);
							hitTypeId = hitTypeIdElement.getTextContent();
							responseMap.put("hitTypeId", hitTypeId);
						}
						
						if(hitId != null && hitTypeId != null){
							String previewUrl = this.getPreviewUrl(hitTypeId);
							responseMap.put("previewUrl", previewUrl);
							System.out.println("createHIT success: " + previewUrl);						
						}
						
					}else{
						System.err.println("createHIT operation: invalid request");
					}
				}
				
				
			} catch(ParserConfigurationException e) {
				e.printStackTrace();
			} catch (SAXException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}		
		return responseMap;
	}

	public Map<String, String> createHITWithExternalQuestion(String title, String description, 
			String questionUrl, int externalFrameHeight, double rewardAmt, long maxAssignments, 
			long assignmentDurationInSeconds, long lifetimeInSeconds, long autoApprovalDelayInSeconds,
			QualificationRequirement qualificationRequirement){
		
			String question = this.getExternalQuestion(questionUrl, externalFrameHeight);
			return this.createHIT(title, description, question, rewardAmt, maxAssignments, 
					assignmentDurationInSeconds, lifetimeInSeconds, autoApprovalDelayInSeconds,
					qualificationRequirement);
	}
	
	public boolean extendHIT(String hitId, int maxAssignmentsIncrement){
		Map<String, String> parameters = new HashMap<String, String>(1);
		parameters.put("HITId", hitId);
		parameters.put("MaxAssignmentsIncrement", Integer.toString(maxAssignmentsIncrement));
		String response = this.makeMTurkRequest(EXTEND_HIT_OPERATION, parameters);
		
		if(response != null && response.length() > 0){
			String responseXML = this.decodeXML(response.toString());
			
			try{
				DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
				DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
				Document doc = dBuilder.parse(new InputSource(new StringReader(responseXML)));
				doc.getDocumentElement().normalize();
				
				NodeList isValidNodeList = doc.getElementsByTagName("IsValid");
				if(isValidNodeList.getLength() > 0){
					Element isValidElement = (Element) isValidNodeList.item(0);
					String isValid = isValidElement.getTextContent();
					
					if(isValid.equals("True")){
						return true;
					}
				}
				
			} catch(ParserConfigurationException e) {
				e.printStackTrace();
			} catch (SAXException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		return false;
		
	}
	
	
	public Map<String, String> getAssignmentsForHIT(String hitId){
		HashMap<String, String> answerMap = new HashMap<String, String>(3);
		
		Map<String, String> parameters = new HashMap<String, String>(1);
		parameters.put("HITId", hitId);
		String response = this.makeMTurkRequest(GET_ASSIGNMENTS_FOR_HIT_OPERATION, parameters);
		
		//parse XML response for worker's submitted answer
		if(response != null && response.length() > 0){
			String responseXML = this.decodeXML(response.toString());
			
			//DOM ran into problems when using it to parse this nested xml
			//so we use substring here
			
			//check value of IsValid node
			String tagName = "<IsValid>";
			int first = responseXML.indexOf(tagName) + tagName.length();
			int last = responseXML.indexOf("</IsValid>");
			String isValidValue = responseXML.substring(first, last);
			
			if(isValidValue.equals("True")){
				//get assignmentId
				tagName = "<AssignmentId>";
				first = responseXML.lastIndexOf(tagName) + tagName.length();
				last = responseXML.lastIndexOf("</AssignmentId>");
				String assignmentId = responseXML.substring(first, last);
				answerMap.put("assignmentId", assignmentId);
				
				//get workerId
				tagName = "<WorkerId>";
				first = responseXML.lastIndexOf(tagName) + tagName.length();
				last = responseXML.lastIndexOf("</WorkerId>");
				String workerId = responseXML.substring(first, last);
				answerMap.put("workerId", workerId);
				
				//get the inner nested xml data
				tagName = "</QuestionFormAnswers>";
				first = responseXML.lastIndexOf("<?xml");
				last = responseXML.lastIndexOf(tagName) + tagName.length();
				responseXML = responseXML.substring(first, last);
				
				try{
					DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
					DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
					Document doc = dBuilder.parse(new InputSource(new StringReader(responseXML)));
					doc.getDocumentElement().normalize();
					
					NodeList nodeList = doc.getElementsByTagName("Answer");
					for(int i = 0; i < nodeList.getLength(); i++){
						Element answerElement = (Element) nodeList.item(i);
						Element idElement = (Element) answerElement.getElementsByTagName("QuestionIdentifier").item(0);
						Element textElement = (Element) answerElement.getElementsByTagName("FreeText").item(0);
						
						String id = idElement.getTextContent();
						String text = textElement.getTextContent();
						answerMap.put(id, text);
					}
					
				} catch(ParserConfigurationException e) {
					e.printStackTrace();
				} catch (SAXException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
				
			}else{
				System.err.println("Request of GetAssignmentsForHIT is invalid");
			}
			
		}

		return answerMap;
	}
	
	public boolean grantBonus(String workerId, String assignmentId, double bonusAmt, String reason){
		Map<String, String> parameters = new HashMap<String, String>(5);
		parameters.put("WorkerId", workerId);
		parameters.put("AssignmentId", assignmentId);
		parameters.put("BonusAmount.1.Amount", Double.toString(bonusAmt));
		parameters.put("BonusAmount.1.CurrencyCode", "USD");
		parameters.put("Reason", reason);

		String response = this.makeMTurkRequest(GRANT_BONUS_OPERATION, parameters);
		if(response != null && response.length() > 0){
			String responseXML = this.decodeXML(response.toString());

			try{
				DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
				DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
				Document doc = dBuilder.parse(new InputSource(new StringReader(responseXML)));
				doc.getDocumentElement().normalize();
				
				NodeList isValidNodeList = doc.getElementsByTagName("IsValid");
				if(isValidNodeList.getLength() > 0){
					Element isValidElement = (Element) isValidNodeList.item(0);
					String isValid = isValidElement.getTextContent();
					
					if(isValid.equals("True")){
						return true;
					}
				}
				
			} catch(ParserConfigurationException e) {
				e.printStackTrace();
			} catch (SAXException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return false;
		
	}

	public boolean rejectAssignment(String assignmentId, String requesterFeedback){

		Map<String, String> parameters = new HashMap<String, String>(5);
		parameters.put("AssignmentId", assignmentId);
		if(requesterFeedback != null && requesterFeedback.length() > 0){
			parameters.put("RequesterFeedback", requesterFeedback);
		}

		String response = this.makeMTurkRequest(REJECT_ASSIGNMENT_OPERATION, parameters);
		if(response != null && response.length() > 0){
			String responseXML = this.decodeXML(response.toString());

			try{
				DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
				DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
				Document doc = dBuilder.parse(new InputSource(new StringReader(responseXML)));
				doc.getDocumentElement().normalize();
				
				NodeList isValidNodeList = doc.getElementsByTagName("IsValid");
				if(isValidNodeList.getLength() > 0){
					Element isValidElement = (Element) isValidNodeList.item(0);
					String isValid = isValidElement.getTextContent();
					
					if(isValid.equals("True")){
						return true;
					}
				}
				
			} catch(ParserConfigurationException e) {
				e.printStackTrace();
			} catch (SAXException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return false;
	}
	

	public boolean setHITTypeNotification(String hitTypeId, String destination, String[] eventTypes, 
			String transport, boolean isMakeActive){	
		
		Map<String, String> parameters = new HashMap<String, String>(5);
		parameters.put("HITTypeId", hitTypeId);
		parameters.put("Active", Boolean.toString(isMakeActive));
		parameters.put("Notification.1.Destination", destination);
		parameters.put("Notification.1.Transport", transport);
		parameters.put("Notification.1.Version", "2006-05-05");
		parameters.put("Notification.1.Destination", destination);
		
		if(eventTypes.length < 1){
			return false;
		}else if(eventTypes.length == 1){
			parameters.put("Notification.1.EventType", eventTypes[0]);
		}else{
			for(int i = 0; i < eventTypes.length; i++){
				parameters.put("Notification.1.EventType." + i, eventTypes[i]);
			}
		}
		
		boolean success = false;
		String response = this.makeMTurkRequest(SET_HITTYPE_NOTIFICATION_OPERATION, parameters);
		if(response != null && response.length() > 0){
			String responseXML = this.decodeXML(response.toString());

			try{
				DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
				DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
				Document doc = dBuilder.parse(new InputSource(new StringReader(responseXML)));
				doc.getDocumentElement().normalize();
				
				NodeList nodeList = doc.getElementsByTagName("IsValid");
				if(nodeList.getLength() > 0){
					Element isValidElement = (Element) nodeList.item(0);
					if(isValidElement.getTextContent().equals("True")){
						success = true;
						System.out.println("Successfully set notification endpoint at:" + destination);
					}				
				}
				
			} catch(ParserConfigurationException e) {
				e.printStackTrace();
			} catch (SAXException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
		}
		
		return success;
	}
	
	public String getPreviewURL(){
		return this.PREVIEW_URL;
	}
	
	/*
	 * private Helper functions
	 */
	private String decodeXML(String rawXML){
		return rawXML.replace("&lt;", "<").replace("&gt;", ">");
	}
	
	private String getExternalQuestion(String url, int externalFrameHeight){
		StringBuffer q = new StringBuffer();
		q.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		q.append("<ExternalQuestion xmlns=\"http://mechanicalturk.amazonaws.com/AWSMechanicalTurkDataSchemas/2006-07-14/ExternalQuestion.xsd\">");
		q.append("	<ExternalURL>" + url + "</ExternalURL>");
		q.append("	<FrameHeight>" + externalFrameHeight + "</FrameHeight>");
		q.append("</ExternalQuestion>");
		
		return q.toString();
	}
	

	/*
	 * Note: HitTypeId can be used for the groupId parameter as well
	 */
	private String getPreviewUrl(String groupId){
		return this.PREVIEW_URL + "groupId=" + groupId;
	}
	
	private String getTimestamp(){
        Calendar cal = Calendar.getInstance();
        DateFormat dfm = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        dfm.setTimeZone(TimeZone.getTimeZone("GMT"));
        String timestamp = dfm.format(cal.getTime());

        return timestamp;
	}
	
	private String getSignature(String operation, String timestamp){
		String signatureData = MTURK_SERVICE_NAME + operation + timestamp;
		
		String signature = null;
		
		try{
			Mac mac = Mac.getInstance("HmacSHA1");
			SecretKeySpec signingKey = new SecretKeySpec(this.SECRET_KEY.getBytes(), mac.getAlgorithm());
			mac.init(signingKey);
			byte[] rawHmac = mac.doFinal(signatureData.getBytes());
			Base64 encoder = new Base64();
			signature = new String(encoder.encode(rawHmac));
		}catch(NoSuchAlgorithmException e){
			
		}catch(InvalidKeyException e){
			e.printStackTrace();
		}
		
		return signature;
	}
			
	private String makeMTurkRequest(String operation, Map<String, String> parameters){
		
		String result = null;
		int i = 0;
		while(i < REST_REQUEST_RETRY_LIMIT){	//loop until success or reach limit
			String timestamp = this.getTimestamp();
			String signature = this.getSignature(operation, timestamp);

			StringBuffer requestUrl = new StringBuffer(this.SERVICE_URL);
			StringBuffer response = new StringBuffer();
			try{
				requestUrl.append("?Service=" + MTURK_SERVICE_NAME);
				requestUrl.append("&AWSAccessKeyId=" + this.ACCESS_KEY);
				requestUrl.append("&Version=" + this.REST_API_VERSION);
				requestUrl.append("&Operation=" + operation);
				requestUrl.append("&Signature=" + signature);
				requestUrl.append("&Timestamp=" + timestamp);
				
				for(Map.Entry<String, String> entry : parameters.entrySet()){
					requestUrl.append("&" + entry.getKey() + "=");
					requestUrl.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
				}
							
				URL url = new URL(requestUrl.toString());
				BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
				String line;
				
				while((line = reader.readLine()) != null){
					response.append(line);
				}
				
				reader.close();
							
			}catch(MalformedURLException e){
				e.printStackTrace();
			}catch(IOException e){
				e.printStackTrace();
			}
			
			result = response.toString();
			if(!result.contains("<Errors>") || result.contains("InvalidAssignmentState")){
				//no error
				break;
			}

			System.err.println("makeMTurkRequest error response: " + result);
			i++;
			
			
			try {
				Thread.sleep(5);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		
		return result;
	}
	
	
}
