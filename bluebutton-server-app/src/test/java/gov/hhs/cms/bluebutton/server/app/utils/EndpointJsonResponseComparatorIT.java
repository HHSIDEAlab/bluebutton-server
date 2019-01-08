package gov.hhs.cms.bluebutton.server.app.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.CapabilityStatement;
import org.hl7.fhir.dstu3.model.Coverage;
import org.hl7.fhir.dstu3.model.ExplanationOfBenefit;
import org.hl7.fhir.dstu3.model.Patient;
import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkart.zjsonpatch.JsonDiff;

import ca.uhn.fhir.rest.client.IGenericClient;
import ca.uhn.fhir.rest.server.EncodingEnum;
import gov.hhs.cms.bluebutton.data.model.rif.Beneficiary;
import gov.hhs.cms.bluebutton.data.model.rif.CarrierClaim;
import gov.hhs.cms.bluebutton.data.model.rif.DMEClaim;
import gov.hhs.cms.bluebutton.data.model.rif.HHAClaim;
import gov.hhs.cms.bluebutton.data.model.rif.HospiceClaim;
import gov.hhs.cms.bluebutton.data.model.rif.InpatientClaim;
import gov.hhs.cms.bluebutton.data.model.rif.OutpatientClaim;
import gov.hhs.cms.bluebutton.data.model.rif.PartDEvent;
import gov.hhs.cms.bluebutton.data.model.rif.SNFClaim;
import gov.hhs.cms.bluebutton.data.model.rif.samples.StaticRifResourceGroup;
import gov.hhs.cms.bluebutton.server.app.ServerTestUtils;
import gov.hhs.cms.bluebutton.server.app.stu3.providers.ClaimType;
import gov.hhs.cms.bluebutton.server.app.stu3.providers.MedicareSegment;
import gov.hhs.cms.bluebutton.server.app.stu3.providers.TransformerConstants;
import gov.hhs.cms.bluebutton.server.app.stu3.providers.TransformerUtils;

/**
 * Integration tests for comparing changes in the JSON from our endpoint
 * responses.
 */
public final class EndpointJsonResponseComparatorIT {

	/**
	 * Verifies that there is no change between the current and approved responses
	 * for the capability statement.
	 * 
	 * @throws IOException
	 *             (indicates a file was not found)
	 */
	@Test
	public void metadata() throws IOException {
		Path targetResponseDir = getTargetResponseDir();

		IGenericClient fhirClient = ServerTestUtils.createFhirClient();
		fhirClient.setEncoding(EncodingEnum.JSON);
		JsonInterceptor jsonInterceptor = new JsonInterceptor();
		fhirClient.registerInterceptor(jsonInterceptor);

		fhirClient.fetchConformance().ofType(CapabilityStatement.class).execute();
		writeFile(jsonInterceptor.getResponse(), generateFileName(targetResponseDir, "metadata"));

		assertJsonDiffIsEmpty("metadata");
	}

	/**
	 * Verifies that there is no change between the current and approved responses
	 * for the patient read endpoint.
	 * 
	 * @throws IOException
	 *             (indicates a file was not found)
	 */
	@Test
	public void patientRead() throws IOException {
		Path targetResponseDir = getTargetResponseDir();

		List<Object> loadedRecords = ServerTestUtils
				.loadData(Arrays.asList(StaticRifResourceGroup.SAMPLE_A.getResources()));
		Beneficiary beneficiary = loadedRecords.stream().filter(r -> r instanceof Beneficiary).map(r -> (Beneficiary) r)
				.findFirst().get();

		IGenericClient fhirClient = ServerTestUtils.createFhirClient();
		fhirClient.setEncoding(EncodingEnum.JSON);
		JsonInterceptor jsonInterceptor = new JsonInterceptor();
		fhirClient.registerInterceptor(jsonInterceptor);

		fhirClient.read(Patient.class, beneficiary.getBeneficiaryId());
		writeFile(jsonInterceptor.getResponse(), generateFileName(targetResponseDir, "patientRead"));

		assertJsonDiffIsEmpty("patientRead");
	}

	/**
	 * Verifies that there is no change between the current and approved responses
	 * for the patient search endpoint.
	 * 
	 * @throws IOException
	 *             (indicates a file was not found)
	 */
	@Test
	public void patientSearchById() throws IOException {
		Path targetResponseDir = getTargetResponseDir();

		List<Object> loadedRecords = ServerTestUtils
				.loadData(Arrays.asList(StaticRifResourceGroup.SAMPLE_A.getResources()));
		Beneficiary beneficiary = loadedRecords.stream().filter(r -> r instanceof Beneficiary).map(r -> (Beneficiary) r)
				.findFirst().get();

		IGenericClient fhirClient = ServerTestUtils.createFhirClient();
		fhirClient.setEncoding(EncodingEnum.JSON);
		JsonInterceptor jsonInterceptor = new JsonInterceptor();
		fhirClient.registerInterceptor(jsonInterceptor);

		fhirClient.search().forResource(Patient.class)
				.where(Patient.RES_ID.exactly().systemAndIdentifier(null, beneficiary.getBeneficiaryId()))
				.returnBundle(Bundle.class).execute();
		writeFile(jsonInterceptor.getResponse(), generateFileName(targetResponseDir, "patientSearchById"));

		assertJsonDiffIsEmpty("patientSearchById");
	}

	/**
	 * Verifies that there is no change between the current and approved responses
	 * for the patient search by hicn endpoint.
	 * 
	 * @throws IOException
	 *             (indicates a file was not found)
	 */
	@Test
	public void patientByIdentifier() throws IOException {
		Path targetResponseDir = getTargetResponseDir();

		List<Object> loadedRecords = ServerTestUtils
				.loadData(Arrays.asList(StaticRifResourceGroup.SAMPLE_A.getResources()));
		Beneficiary beneficiary = loadedRecords.stream().filter(r -> r instanceof Beneficiary).map(r -> (Beneficiary) r)
				.findFirst().get();

		IGenericClient fhirClient = ServerTestUtils.createFhirClient();
		fhirClient.setEncoding(EncodingEnum.JSON);
		JsonInterceptor jsonInterceptor = new JsonInterceptor();
		fhirClient.registerInterceptor(jsonInterceptor);


		fhirClient.search().forResource(Patient.class)
				.where(Patient.IDENTIFIER.exactly()
						.systemAndIdentifier(TransformerConstants.CODING_BBAPI_BENE_HICN_HASH, beneficiary.getHicn()))
				.returnBundle(Bundle.class).execute();
		writeFile(jsonInterceptor.getResponse(), generateFileName(targetResponseDir, "patientByIdentifier"));

		assertJsonDiffIsEmpty("patientByIdentifier");
	}

	/**
	 * Verifies that there is no change between the current and approved responses
	 * for the coverage read endpoint.
	 * 
	 * @throws IOException
	 *             (indicates a file was not found)
	 */
	@Test
	public void coverageRead() throws IOException {
		Path targetResponseDir = getTargetResponseDir();

		List<Object> loadedRecords = ServerTestUtils
				.loadData(Arrays.asList(StaticRifResourceGroup.SAMPLE_A.getResources()));
		Beneficiary beneficiary = loadedRecords.stream().filter(r -> r instanceof Beneficiary).map(r -> (Beneficiary) r)
				.findFirst().get();

		IGenericClient fhirClient = ServerTestUtils.createFhirClient();
		fhirClient.setEncoding(EncodingEnum.JSON);
		JsonInterceptor jsonInterceptor = new JsonInterceptor();
		fhirClient.registerInterceptor(jsonInterceptor);

		Coverage partACoverage = fhirClient.read(Coverage.class,
				TransformerUtils.buildCoverageId(MedicareSegment.PART_A, beneficiary.getBeneficiaryId()));
		Assert.assertNotNull(partACoverage);
		writeFile(jsonInterceptor.getResponse(), generateFileName(targetResponseDir, "coverageRead"));

		assertJsonDiffIsEmpty("coverageRead");
	}

	/**
	 * Verifies that there is no change between the current and approved responses
	 * for the coverage search endpoint.
	 * 
	 * @throws IOException
	 *             (indicates a file was not found)
	 */
	@Test
	public void coverageSearchByPatientId() throws IOException {
		Path targetResponseDir = getTargetResponseDir();

		List<Object> loadedRecords = ServerTestUtils
				.loadData(Arrays.asList(StaticRifResourceGroup.SAMPLE_A.getResources()));
		Beneficiary beneficiary = loadedRecords.stream().filter(r -> r instanceof Beneficiary).map(r -> (Beneficiary) r)
				.findFirst().get();

		IGenericClient fhirClient = ServerTestUtils.createFhirClient();
		fhirClient.setEncoding(EncodingEnum.JSON);
		JsonInterceptor jsonInterceptor = new JsonInterceptor();
		fhirClient.registerInterceptor(jsonInterceptor);

		Bundle coverageBundle = fhirClient.search().forResource(Coverage.class)
				.where(Coverage.BENEFICIARY.hasId(TransformerUtils.buildPatientId(beneficiary.getBeneficiaryId())))
				.returnBundle(Bundle.class).execute();
		Assert.assertNotNull(coverageBundle);
		writeFile(jsonInterceptor.getResponse(), generateFileName(targetResponseDir, "coverageSearchByPatientId"));

		assertJsonDiffIsEmpty("coverageSearchByPatientId");
	}

	/**
	 * Verifies that there is no change between the current and approved responses
	 * for the patient search by id endpoint.
	 * 
	 * @throws IOException
	 *             (indicates a file was not found)
	 */
	@Test
	public void eobByPatientIdAll() throws IOException {
		Path targetResponseDir = getTargetResponseDir();

		List<Object> loadedRecords = ServerTestUtils
				.loadData(Arrays.asList(StaticRifResourceGroup.SAMPLE_A.getResources()));
		Beneficiary beneficiary = loadedRecords.stream().filter(r -> r instanceof Beneficiary).map(r -> (Beneficiary) r)
				.findFirst().get();

		IGenericClient fhirClient = ServerTestUtils.createFhirClient();
		fhirClient.setEncoding(EncodingEnum.JSON);
		JsonInterceptor jsonInterceptor = new JsonInterceptor();
		fhirClient.registerInterceptor(jsonInterceptor);

		fhirClient.search().forResource(ExplanationOfBenefit.class)
				.where(ExplanationOfBenefit.PATIENT.hasId(TransformerUtils.buildPatientId(beneficiary)))
				.returnBundle(Bundle.class).execute();
		writeFile(jsonInterceptor.getResponse(), generateFileName(targetResponseDir, "eobByPatientIdAll"));

		assertJsonDiffIsEmpty("eobByPatientIdAll");
	}

	/**
	 * Verifies that there is no change between the current and approved responses
	 * for the patient search by id with paging endpoint.
	 * 
	 * @throws IOException
	 *             (indicates a file was not found)
	 */
	@Test
	public void eobByPatientIdPaged() throws IOException {
		Path targetResponseDir = getTargetResponseDir();

		List<Object> loadedRecords = ServerTestUtils
				.loadData(Arrays.asList(StaticRifResourceGroup.SAMPLE_A.getResources()));
		Beneficiary beneficiary = loadedRecords.stream().filter(r -> r instanceof Beneficiary).map(r -> (Beneficiary) r)
				.findFirst().get();

		IGenericClient fhirClient = ServerTestUtils.createFhirClient();
		fhirClient.setEncoding(EncodingEnum.JSON);
		JsonInterceptor jsonInterceptor = new JsonInterceptor();
		fhirClient.registerInterceptor(jsonInterceptor);

		fhirClient.search().forResource(ExplanationOfBenefit.class)
				.where(ExplanationOfBenefit.PATIENT.hasId(TransformerUtils.buildPatientId(beneficiary))).count(8)
				.returnBundle(Bundle.class).execute();
		writeFile(jsonInterceptor.getResponse(), generateFileName(targetResponseDir, "eobByPatientIdPaged"));

		assertJsonDiffIsEmpty("eobByPatientIdPaged");
	}

	/**
	 * Verifies that there is no change between the current and approved responses
	 * for the carrier read endpoint.
	 * 
	 * @throws IOException
	 *             (indicates a file was not found)
	 */
	@Test
	public void eobReadCarrier() throws IOException {
		Path targetResponseDir = getTargetResponseDir();

		List<Object> loadedRecords = ServerTestUtils
				.loadData(Arrays.asList(StaticRifResourceGroup.SAMPLE_A.getResources()));

		IGenericClient fhirClient = ServerTestUtils.createFhirClient();
		fhirClient.setEncoding(EncodingEnum.JSON);
		JsonInterceptor jsonInterceptor = new JsonInterceptor();
		fhirClient.registerInterceptor(jsonInterceptor);

		CarrierClaim carrClaim = loadedRecords.stream().filter(r -> r instanceof CarrierClaim)
				.map(r -> (CarrierClaim) r).findFirst().get();
		ExplanationOfBenefit carrEob = fhirClient.read(ExplanationOfBenefit.class,
				TransformerUtils.buildEobId(ClaimType.CARRIER, carrClaim.getClaimId()));
		Assert.assertNotNull(carrEob);
		writeFile(jsonInterceptor.getResponse(), generateFileName(targetResponseDir, "eobReadCarrier"));

		assertJsonDiffIsEmpty("eobReadCarrier");
	}

	/**
	 * Verifies that there is no change between the current and approved responses
	 * for the dme read endpoint.
	 * 
	 * @throws IOException
	 *             (indicates a file was not found)
	 */
	@Test
	public void eobReadDme() throws IOException {
		Path targetResponseDir = getTargetResponseDir();

		List<Object> loadedRecords = ServerTestUtils
				.loadData(Arrays.asList(StaticRifResourceGroup.SAMPLE_A.getResources()));

		IGenericClient fhirClient = ServerTestUtils.createFhirClient();
		fhirClient.setEncoding(EncodingEnum.JSON);
		JsonInterceptor jsonInterceptor = new JsonInterceptor();
		fhirClient.registerInterceptor(jsonInterceptor);

		DMEClaim dmeClaim = loadedRecords.stream().filter(r -> r instanceof DMEClaim).map(r -> (DMEClaim) r).findFirst()
				.get();
		ExplanationOfBenefit dmeEob = fhirClient.read(ExplanationOfBenefit.class,
				TransformerUtils.buildEobId(ClaimType.DME, dmeClaim.getClaimId()));
		Assert.assertNotNull(dmeEob);
		writeFile(jsonInterceptor.getResponse(), generateFileName(targetResponseDir, "eobReadDme"));

		assertJsonDiffIsEmpty("eobReadDme");
	}

	/**
	 * Verifies that there is no change between the current and approved responses
	 * for the hha read endpoint.
	 * 
	 * @throws IOException
	 *             (indicates a file was not found)
	 */
	@Test
	public void eobReadHha() throws IOException {
		Path targetResponseDir = getTargetResponseDir();

		List<Object> loadedRecords = ServerTestUtils
				.loadData(Arrays.asList(StaticRifResourceGroup.SAMPLE_A.getResources()));

		IGenericClient fhirClient = ServerTestUtils.createFhirClient();
		fhirClient.setEncoding(EncodingEnum.JSON);
		JsonInterceptor jsonInterceptor = new JsonInterceptor();
		fhirClient.registerInterceptor(jsonInterceptor);

		HHAClaim hhaClaim = loadedRecords.stream().filter(r -> r instanceof HHAClaim).map(r -> (HHAClaim) r).findFirst()
				.get();
		ExplanationOfBenefit hhaEob = fhirClient.read(ExplanationOfBenefit.class,
				TransformerUtils.buildEobId(ClaimType.HHA, hhaClaim.getClaimId()));
		Assert.assertNotNull(hhaEob);
		writeFile(jsonInterceptor.getResponse(), generateFileName(targetResponseDir, "eobReadHha"));

		assertJsonDiffIsEmpty("eobReadHha");
	}

	/**
	 * Verifies that there is no change between the current and approved responses
	 * for the hospice read endpoint.
	 * 
	 * @throws IOException
	 *             (indicates a file was not found)
	 */
	@Test
	public void eobReadHospice() throws IOException {
		Path targetResponseDir = getTargetResponseDir();

		List<Object> loadedRecords = ServerTestUtils
				.loadData(Arrays.asList(StaticRifResourceGroup.SAMPLE_A.getResources()));

		IGenericClient fhirClient = ServerTestUtils.createFhirClient();
		fhirClient.setEncoding(EncodingEnum.JSON);
		JsonInterceptor jsonInterceptor = new JsonInterceptor();
		fhirClient.registerInterceptor(jsonInterceptor);

		HospiceClaim hosClaim = loadedRecords.stream().filter(r -> r instanceof HospiceClaim).map(r -> (HospiceClaim) r)
				.findFirst().get();
		ExplanationOfBenefit hosEob = fhirClient.read(ExplanationOfBenefit.class,
				TransformerUtils.buildEobId(ClaimType.HOSPICE, hosClaim.getClaimId()));
		Assert.assertNotNull(hosEob);
		writeFile(jsonInterceptor.getResponse(), generateFileName(targetResponseDir, "eobReadHospice"));

		assertJsonDiffIsEmpty("eobReadHospice");
	}

	/**
	 * Verifies that there is no change between the current and approved responses
	 * for the inpatient read endpoint.
	 * 
	 * @throws IOException
	 *             (indicates a file was not found)
	 */
	@Test
	public void eobReadInpatient() throws IOException {
		Path targetResponseDir = getTargetResponseDir();

		List<Object> loadedRecords = ServerTestUtils
				.loadData(Arrays.asList(StaticRifResourceGroup.SAMPLE_A.getResources()));

		IGenericClient fhirClient = ServerTestUtils.createFhirClient();
		fhirClient.setEncoding(EncodingEnum.JSON);
		JsonInterceptor jsonInterceptor = new JsonInterceptor();
		fhirClient.registerInterceptor(jsonInterceptor);

		InpatientClaim inpClaim = loadedRecords.stream().filter(r -> r instanceof InpatientClaim)
				.map(r -> (InpatientClaim) r).findFirst().get();
		ExplanationOfBenefit inpEob = fhirClient.read(ExplanationOfBenefit.class,
				TransformerUtils.buildEobId(ClaimType.INPATIENT, inpClaim.getClaimId()));
		Assert.assertNotNull(inpEob);
		writeFile(jsonInterceptor.getResponse(), generateFileName(targetResponseDir, "eobReadInpatient"));

		assertJsonDiffIsEmpty("eobReadInpatient");
	}

	/**
	 * Verifies that there is no change between the current and approved responses
	 * for the outpatient read endpoint.
	 * 
	 * @throws IOException
	 *             (indicates a file was not found)
	 */
	@Test
	public void eobReadOutpatient() throws IOException {
		Path targetResponseDir = getTargetResponseDir();

		List<Object> loadedRecords = ServerTestUtils
				.loadData(Arrays.asList(StaticRifResourceGroup.SAMPLE_A.getResources()));

		IGenericClient fhirClient = ServerTestUtils.createFhirClient();
		fhirClient.setEncoding(EncodingEnum.JSON);
		JsonInterceptor jsonInterceptor = new JsonInterceptor();
		fhirClient.registerInterceptor(jsonInterceptor);

		OutpatientClaim outClaim = loadedRecords.stream().filter(r -> r instanceof OutpatientClaim)
				.map(r -> (OutpatientClaim) r).findFirst().get();
		ExplanationOfBenefit outEob = fhirClient.read(ExplanationOfBenefit.class,
				TransformerUtils.buildEobId(ClaimType.OUTPATIENT, outClaim.getClaimId()));
		Assert.assertNotNull(outEob);
		writeFile(jsonInterceptor.getResponse(), generateFileName(targetResponseDir, "eobReadOutpatient"));

		assertJsonDiffIsEmpty("eobReadOutpatient");
	}

	/**
	 * Verifies that there is no change between the current and approved responses
	 * for the pde read endpoint.
	 * 
	 * @throws IOException
	 *             (indicates a file was not found)
	 */
	@Test
	public void eobReadPde() throws IOException {
		Path targetResponseDir = getTargetResponseDir();

		List<Object> loadedRecords = ServerTestUtils
				.loadData(Arrays.asList(StaticRifResourceGroup.SAMPLE_A.getResources()));

		IGenericClient fhirClient = ServerTestUtils.createFhirClient();
		fhirClient.setEncoding(EncodingEnum.JSON);
		JsonInterceptor jsonInterceptor = new JsonInterceptor();
		fhirClient.registerInterceptor(jsonInterceptor);

		PartDEvent pdeClaim = loadedRecords.stream().filter(r -> r instanceof PartDEvent).map(r -> (PartDEvent) r)
				.findFirst().get();
		ExplanationOfBenefit pdeEob = fhirClient.read(ExplanationOfBenefit.class,
				TransformerUtils.buildEobId(ClaimType.PDE, pdeClaim.getEventId()));
		Assert.assertNotNull(pdeEob);
		writeFile(jsonInterceptor.getResponse(), generateFileName(targetResponseDir, "eobReadPde"));

		assertJsonDiffIsEmpty("eobReadPde");
	}

	/**
	 * Verifies that there is no change between the current and approved responses
	 * for the snf read endpoint.
	 * 
	 * @throws IOException
	 *             (indicates a file was not found)
	 */
	@Test
	public void eobReadSnf() throws IOException {
		Path targetResponseDir = getTargetResponseDir();

		List<Object> loadedRecords = ServerTestUtils
				.loadData(Arrays.asList(StaticRifResourceGroup.SAMPLE_A.getResources()));

		IGenericClient fhirClient = ServerTestUtils.createFhirClient();
		fhirClient.setEncoding(EncodingEnum.JSON);
		JsonInterceptor jsonInterceptor = new JsonInterceptor();
		fhirClient.registerInterceptor(jsonInterceptor);

		SNFClaim snfClaim = loadedRecords.stream().filter(r -> r instanceof SNFClaim).map(r -> (SNFClaim) r).findFirst()
				.get();
		ExplanationOfBenefit snfEob = fhirClient.read(ExplanationOfBenefit.class,
				TransformerUtils.buildEobId(ClaimType.SNF, snfClaim.getClaimId()));
		Assert.assertNotNull(snfEob);
		writeFile(jsonInterceptor.getResponse(), generateFileName(targetResponseDir, "eobReadSnf"));

		assertJsonDiffIsEmpty("eobReadSnf");
	}

	/**
	 * @param fileName
	 *            A string to determine which files to compare. Compares the
	 *            approved and current responses for an endpoint.
	 * @throws IOException
	 *             (indicates a file was not found)
	 */
	private void assertJsonDiffIsEmpty(String fileName) throws IOException {
		Path approvedResponseDir = Paths.get(".", "src", "test", "resources", "endpoint-responses");
		if (!Files.isDirectory(approvedResponseDir))
			throw new IllegalStateException();
		Path targetResponseDir = getTargetResponseDir();

		String approvedJson;
		try {
			approvedJson = readFile(generateFileName(approvedResponseDir, fileName), Charset.defaultCharset());
		} catch (IOException e) {
			throw new FileNotFoundException(
					"Can't read file at " + generateFileName(approvedResponseDir, fileName).toString());
		}
		
		String newJson;
		try {
			newJson = readFile(generateFileName(targetResponseDir, fileName), Charset.defaultCharset());
		} catch (IOException e) {
			throw new FileNotFoundException(
					"Can't read file at " + generateFileName(targetResponseDir, fileName).toString());
		}
			

		ObjectMapper mapper = new ObjectMapper();
		JsonNode beforeNode = mapper.readTree(approvedJson);
		JsonNode afterNode = mapper.readTree(newJson);
		JsonNode patch = JsonDiff.asJson(beforeNode, afterNode);

		// TODO: Is there a better way to do this? Streams/filters...
		Iterator<JsonNode> iterator = patch.elements();
		String diff = "";
		while (iterator.hasNext()) {
			JsonNode node = iterator.next();
			String pathValue = node.get("path").toString();
			if (!pathValue.contentEquals("\"/id\"") && !pathValue.contentEquals("\"/meta/lastUpdated\"")) {
				// We ignore any updates to the id or lastUpdated fields as they will change
				// with each call to the endpoint.
				diff = diff + node.toString();
			}
		}

		Assert.assertEquals("", diff);
	}

	/*
	 * Returns the path to where a file should be written.
	 */
	private Path getTargetResponseDir() {
		// Path targetResponseDir = Paths.get(".", "src", "test", "resources",
		// "endpoint-responses");
		Path targetResponseDir = Paths.get(".", "target", "endpoint-responses");
		if (!Files.isDirectory(targetResponseDir))
			new File(targetResponseDir.toString()).mkdirs();

		return targetResponseDir;
	}

	/*
	 * @param directory 
	 *            The path to where the file should should be written
	 * @param endpoint 
	 * 			  The string to identify which endpoint's response the
	 * 			  file contents contain
	 * @return Returns a path to use as a filename
	 */
	private static Path generateFileName(Path directory, String endpoint) {
		return Paths.get(directory.toString(), endpoint + ".json");
	}

	/*
	 * @param contents
	 *            The string to be written to a file
	 * @param fileName
	 * 			  The path to name the file
	 */
	private static void writeFile(String contents, Path fileName) {
		try {
			File jsonFile = new File(fileName.toString());
			FileWriter fileWriter = new FileWriter(jsonFile);
			fileWriter.write(contents);
			fileWriter.flush();
			fileWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/*
	 * @param path
	 *            The path to the file
	 * @param encoding
	 * 			  The charset with which to decode the bytes
	 */
	private static String readFile(Path path, Charset encoding) throws IOException {
		byte[] encoded = Files.readAllBytes(path);
		return new String(encoded, encoding);
	}
}