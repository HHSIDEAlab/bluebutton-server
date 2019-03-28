package gov.hhs.cms.bluebutton.server.app.stu3.providers;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.persistence.metamodel.PluralAttribute;
import javax.persistence.metamodel.SingularAttribute;

import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.dstu3.model.Bundle.BundleLinkComponent;
import org.hl7.fhir.dstu3.model.ExplanationOfBenefit;
import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.fhir.dstu3.model.Resource;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.justdavis.karl.misc.exceptions.BadCodeMonkeyException;

import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.RequiredParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import gov.hhs.cms.bluebutton.data.model.rif.Beneficiary;

/**
 * This FHIR {@link IResourceProvider} adds support for STU3
 * {@link ExplanationOfBenefit} resources, derived from the CCW claims.
 */
@Component
public final class ExplanationOfBenefitResourceProvider implements IResourceProvider {

	private static final Logger LOGGER = LoggerFactory.getLogger(ExplanationOfBenefitResourceProvider.class);
	/**
	 * A {@link Pattern} that will match the
	 * {@link ExplanationOfBenefit#getId()}s used in this application.
	 */
	private static final Pattern EOB_ID_PATTERN = Pattern.compile("(\\p{Alpha}+)-(\\p{Alnum}+)");

	private EntityManager entityManager;
	private MetricRegistry metricRegistry;
	private SamhsaMatcher samhsaMatcher;

	/**
	 * @param entityManager
	 *            a JPA {@link EntityManager} connected to the application's
	 *            database
	 */
	@PersistenceContext
	public void setEntityManager(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	/**
	 * @param metricRegistry
	 *            the {@link MetricRegistry} to use
	 */
	@Inject
	public void setMetricRegistry(MetricRegistry metricRegistry) {
		this.metricRegistry = metricRegistry;
	}

	/**
	 * @param samhsaMatcher
	 *            the {@link SamhsaMatcher} to use
	 */
	@Inject
	public void setSamhsaFilterer(SamhsaMatcher samhsaMatcher) {
		this.samhsaMatcher = samhsaMatcher;
	}

	/**
	 * @see ca.uhn.fhir.rest.server.IResourceProvider#getResourceType()
	 */
	@Override
	public Class<? extends IBaseResource> getResourceType() {
		return ExplanationOfBenefit.class;
	}

	/**
	 * <p>
	 * Adds support for the FHIR "read" operation, for
	 * {@link ExplanationOfBenefit}s. The {@link Read} annotation indicates that
	 * this method supports the read operation.
	 * </p>
	 * <p>
	 * Read operations take a single parameter annotated with {@link IdParam},
	 * and should return a single resource instance.
	 * </p>
	 * 
	 * @param eobId
	 *            The read operation takes one parameter, which must be of type
	 *            {@link IdType} and must be annotated with the {@link IdParam}
	 *            annotation.
	 * @return Returns a resource matching the specified {@link IdDt}, or
	 *         <code>null</code> if none exists.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Read(version = false)
	public ExplanationOfBenefit read(@IdParam IdType eobId) {
		if (eobId == null)
			throw new IllegalArgumentException();
		if (eobId.getVersionIdPartAsLong() != null)
			throw new IllegalArgumentException();

		String eobIdText = eobId.getIdPart();
		if (eobIdText == null || eobIdText.trim().isEmpty())
			throw new IllegalArgumentException();

		Matcher eobIdMatcher = EOB_ID_PATTERN.matcher(eobIdText);
		if (!eobIdMatcher.matches())
			throw new ResourceNotFoundException(eobId);
		String eobIdTypeText = eobIdMatcher.group(1);
		Optional<ClaimType> eobIdType = ClaimType.parse(eobIdTypeText);
		if (!eobIdType.isPresent())
			throw new ResourceNotFoundException(eobId);
		String eobIdClaimIdText = eobIdMatcher.group(2);

		Timer.Context timerEobQuery = metricRegistry
				.timer(MetricRegistry.name(getClass().getSimpleName(), "query", "eob_by_id")).time();
		Class<?> entityClass = eobIdType.get().getEntityClass();
		CriteriaBuilder builder = entityManager.getCriteriaBuilder();
		CriteriaQuery criteria = builder.createQuery(entityClass);
		Root root = criteria.from(entityClass);
		eobIdType.get().getEntityLazyAttributes().stream().forEach(a -> root.fetch(a));
		criteria.select(root);
		criteria.where(builder.equal(root.get(eobIdType.get().getEntityIdAttribute()), eobIdClaimIdText));

		Object claimEntity = null;
		try {
			claimEntity = entityManager.createQuery(criteria).getSingleResult();
		} catch (NoResultException e) {
			throw new ResourceNotFoundException(eobId);
		} finally {
			timerEobQuery.stop();
		}

		ExplanationOfBenefit eob = eobIdType.get().getTransformer().apply(metricRegistry, claimEntity);
		return eob;
	}

	/**
	 * <p>
	 * Adds support for the FHIR "search" operation for
	 * {@link ExplanationOfBenefit}s, allowing users to search by
	 * {@link ExplanationOfBenefit#getPatient()}.
	 * </p>
	 * <p>
	 * The {@link Search} annotation indicates that this method supports the search
	 * operation. There may be many different methods annotated with this
	 * {@link Search} annotation, to support many different search criteria.
	 * </p>
	 * 
	 * @param patient
	 *            a {@link ReferenceParam} for the
	 *            {@link ExplanationOfBenefit#getPatient()} to try and find matches
	 *            for {@link ExplanationOfBenefit}s
	 * @param startIndex
	 *            an {@link OptionalParam} for the startIndex (or offset) used to
	 *            determine pagination
	 * @param excludeSamhsa
	 *            an {@link OptionalParam} that, if <code>"true"</code>, will use
	 *            {@link SamhsaMatcher} to filter out all SAMHSA-related claims from
	 *            the results
	 * @param requestDetails
	 *            a {@link RequestDetails} containing the details of the request
	 *            URL, used to parse out pagination values
	 * @return Returns a {@link Bundle} of {@link ExplanationOfBenefit}s, which may
	 *         contain multiple matching resources, or may also be empty.
	 */
	@Search
	public Bundle findByPatient(
			@RequiredParam(name = ExplanationOfBenefit.SP_PATIENT) ReferenceParam patient,
			@OptionalParam(name = "startIndex") String startIndex,
			@OptionalParam(name = "excludeSAMHSA") String excludeSamhsa,
			RequestDetails requestDetails) {
		/*
		 * startIndex is an optional parameter here because it must be declared in the
		 * event it is passed in. However, it is not being used here because it is also
		 * contained within requestDetails and parsed out along with other parameters
		 * later.
		 */

		String beneficiaryId = patient.getIdPart();

		List<ExplanationOfBenefit> eobs = new ArrayList<ExplanationOfBenefit>();
		/*
		 * The way our JPA/SQL schema is setup, we have to run a separate search for
		 * each claim type, then combine the results. It's not super efficient, but it's
		 * also not so inefficient that it's worth fixing.
		 */
		eobs.addAll(transformToEobs(ClaimType.CARRIER, findClaimTypeByPatient(ClaimType.CARRIER, beneficiaryId)));
		eobs.addAll(transformToEobs(ClaimType.DME, findClaimTypeByPatient(ClaimType.DME, beneficiaryId)));
		eobs.addAll(transformToEobs(ClaimType.HHA, findClaimTypeByPatient(ClaimType.HHA, beneficiaryId)));
		eobs.addAll(transformToEobs(ClaimType.HOSPICE, findClaimTypeByPatient(ClaimType.HOSPICE, beneficiaryId)));
		eobs.addAll(transformToEobs(ClaimType.INPATIENT, findClaimTypeByPatient(ClaimType.INPATIENT, beneficiaryId)));
		eobs.addAll(transformToEobs(ClaimType.OUTPATIENT, findClaimTypeByPatient(ClaimType.OUTPATIENT, beneficiaryId)));
		eobs.addAll(transformToEobs(ClaimType.PDE, findClaimTypeByPatient(ClaimType.PDE, beneficiaryId)));
		eobs.addAll(transformToEobs(ClaimType.SNF, findClaimTypeByPatient(ClaimType.SNF, beneficiaryId)));

		if (Boolean.parseBoolean(excludeSamhsa) == true)
			filterSamhsa(eobs);

		eobs.sort(ExplanationOfBenefitResourceProvider::compareByClaimIdThenClaimType);

		Bundle bundle = new Bundle();
		PagingArguments pagingArgs = new PagingArguments(requestDetails);
		if (pagingArgs.isPagingRequested()) {
			/*
			 * A page size of 0 is odd enough that we should throw an exception.
			 */
			if (pagingArgs.getPageSize() == 0) {
				throw new InvalidRequestException("Invalid request - the page size should not be zero.");
			}

			int numToReturn = Math.min(pagingArgs.getPageSize(), eobs.size());
			List<ExplanationOfBenefit> resources = eobs.subList(pagingArgs.getStartIndex(),
					pagingArgs.getStartIndex() + numToReturn);
			bundle = addResourcesToBundle(bundle, resources);
			addPagingLinks(bundle, pagingArgs, beneficiaryId, eobs.size());
		} else {
			bundle = addResourcesToBundle(bundle, eobs);
		}

		bundle.setTotal(eobs.size());

		return bundle;
	}

	/*
	 * @param eob1 an {@link ExplanationOfBenefit} to be compared
	 * 
	 * @param eob2 an {@link ExplanationOfBenefit} to be compared
	 */
	private static int compareByClaimIdThenClaimType(ExplanationOfBenefit eob1, ExplanationOfBenefit eob2) {
		/*
		 * In order for paging to be meaningful (and stable), the claims have to be
		 * consistently sorted across different app server instances (in case page 1
		 * comes from Server A but page 2 comes from Server B). Right now, we don't have
		 * anything "useful" to sort by, so we just sort by claim ID (subsorted by claim
		 * type). TODO once we have metadata from BLUEBUTTON-XXX on when each claim was
		 * first loaded into our DB, we should sort by that.
		 */
		if (TransformerUtils.getUnprefixedClaimId(eob1) == TransformerUtils.getUnprefixedClaimId(eob2)) {
			return TransformerUtils.getClaimType(eob1).compareTo(TransformerUtils.getClaimType(eob2));
		} else {
			return TransformerUtils.getUnprefixedClaimId(eob1).compareTo(TransformerUtils.getUnprefixedClaimId(eob2));
		}
	}

	/**
	 * @param bunlde
	 *            a {@link Bundle} to add the list of {@link ExplanationOfBenefit}
	 *            resources to.
	 * @param eobs
	 *            a list of {@link ExplanationOfBenefit}, of which a portion will be
	 *            added to the bundle based on the paging values
	 * @return Returns a {@link Bundle} of {@link ExplanationOfBenefit}s, which may
	 *         contain multiple matching resources, or may also be empty.
	 */
	private Bundle addResourcesToBundle(Bundle bundle, List<ExplanationOfBenefit> eobs) {
		for (IBaseResource res : eobs) {
			BundleEntryComponent entry = bundle.addEntry();
			entry.setResource((Resource) res);
		}

		return bundle;
	}

	/**
	 * @param bundle
	 *            the {@link Bundle} to which links are being added
	 * @param pagingArgs
	 *            the {@link PagingArguments} containing the parsed parameters for
	 *            the paging URLs
	 * @param beneficiaryId
	 *            the {@link Beneficiary#getBeneficiaryId()} to include in the links
	 * @param numTotalResults
	 *            the number of total resources matching the
	 *            {@link Beneficiary#getBeneficiaryId()}
	 */
	private void addPagingLinks(Bundle bundle, PagingArguments pagingArgs, String beneficiaryId, int numTotalResults) {

		Integer pageSize = pagingArgs.getPageSize();
		Integer startIndex = pagingArgs.getStartIndex();
		String serverBase = pagingArgs.getServerBase();

		if (startIndex + pageSize < numTotalResults) {
			bundle.addLink(new BundleLinkComponent().setRelation(Bundle.LINK_NEXT)
					.setUrl(createPagingLink(serverBase, beneficiaryId, startIndex + pageSize, pageSize)));

			int start = (numTotalResults / pageSize - 1) * pageSize;
			bundle.addLink(new BundleLinkComponent().setRelation("last")
					.setUrl(createPagingLink(serverBase, beneficiaryId, start, pageSize)));
		}

		if (startIndex > 0) {
			int start = Math.max(0, startIndex - pageSize);
			bundle.addLink(new BundleLinkComponent().setRelation(Bundle.LINK_PREV)
					.setUrl(createPagingLink(serverBase, beneficiaryId, start, pageSize)));
		}
	}

	/**
	 * @return Returns the URL string for a paging link.
	 */
	private String createPagingLink(String theServerBase, String patientId, int startIndex, int theCount) {
		StringBuilder b = new StringBuilder();
		b.append(theServerBase + "/ExplanationOfBenefit?");
		b.append("_count=" + theCount);
		b.append("&startIndex=" + startIndex);
		b.append("&patient=" + patientId);

		return b.toString();
	}

	/**
	 * @param claimType
	 *            the {@link ClaimType} to find
	 * @param patientId
	 *            the {@link Beneficiary#getBeneficiaryId()} to filter by
	 * @return the matching claim/event entities
	 */
	@SuppressWarnings({ "rawtypes", "unchecked"})
	private <T> List<T> findClaimTypeByPatient(ClaimType claimType, String patientId) {
		Timer.Context timerEobQuery = metricRegistry.timer(MetricRegistry
				.name(metricRegistry.getClass().getSimpleName(), "query", "eobs", claimType.name().toLowerCase()))
				.time();
		try {
			CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
			CriteriaQuery criteria = criteriaBuilder.createQuery((Class) claimType.getEntityClass());
			Root root = criteria.from((Class) claimType.getEntityClass());
			claimType.getEntityLazyAttributes().stream().forEach(a -> root.fetch((PluralAttribute) a));
			criteria.select(root).distinct(true);
			criteria.where(criteriaBuilder
					.equal(root.get((SingularAttribute) claimType.getEntityBeneficiaryIdAttribute()), patientId));

			List claimEntities = entityManager.createQuery(criteria).getResultList();
			return claimEntities;
		} finally {
			timerEobQuery.stop();
		}
	}

	/**
	 * @param claimType
	 *            the {@link ClaimType} being transformed
	 * @param claims
	 *            the claims/events to transform
	 * @return the transformed {@link ExplanationOfBenefit} instances, one for each
	 *         specified claim/event
	 */
	private List<ExplanationOfBenefit> transformToEobs(ClaimType claimType, List<?> claims) {
		return claims.stream().map(c -> claimType.getTransformer().apply(metricRegistry, c))
				.collect(Collectors.toList());
	}

	/**
	 * Removes all SAMHSA-related claims from the specified {@link List} of
	 * {@link ExplanationOfBenefit} resources.
	 *
	 * @param eobs
	 *            the {@link List} of {@link ExplanationOfBenefit} resources (i.e.
	 *            claims) to filter
	 */
	private void filterSamhsa(List<ExplanationOfBenefit> eobs) {
		ListIterator<ExplanationOfBenefit> eobsIter = eobs.listIterator();
		while (eobsIter.hasNext()) {
			ExplanationOfBenefit eob = eobsIter.next();
			if (samhsaMatcher.test(eob))
				eobsIter.remove();
		}
	}

	/*
	 * PagingArguments encapsulates the arguments related to paging for an 
	 * {@link ExplanationOfBenefit} request.
	 */
	private static final class PagingArguments {
		private final Optional<Integer> pageSize;
		private final Optional<Integer> startIndex;
		private final String serverBase;

		public PagingArguments(RequestDetails requestDetails) {
			pageSize = parseIntegerParameters(requestDetails, "_count");
			startIndex = parseIntegerParameters(requestDetails, "startIndex");
			serverBase = requestDetails.getServerBaseForRequest();
		}

		/**
		 * @param requestDetails
		 *            the {@link RequestDetails} containing additional parameters for
		 *            the URL in need of parsing out
		 * @param parameterToParse
		 *            the parameter to parse from requestDetails
		 * @return Returns the parsed parameter as an Integer, null if the parameter is
		 *         not found.
		 */
		private Optional<Integer> parseIntegerParameters(RequestDetails requestDetails, String parameterToParse) {
			if (requestDetails.getParameters().containsKey(parameterToParse)) {
				try {
					return Optional.of(Integer.parseInt(requestDetails.getParameters().get(parameterToParse)[0]));
				} catch (NumberFormatException e) {
					LOGGER.warn("Invalid argument in request URL: " + parameterToParse + ". Cannot parse to Integer.",
							e);
					throw new InvalidRequestException(
							"Invalid argument in request URL: " + parameterToParse + ". Cannot parse to Integer.");
				}
			}
			return Optional.empty();
		}

		/*
		 * @return Returns true if the pageSize or startIndex is present (i.e. paging is
		 * 		   requested), false if they are not present, and throws an
		 *         IllegalArgumentException if the arguments are mismatched.
		 */
		public boolean isPagingRequested() {
			if (pageSize.isPresent())
				return true;
			else if (!pageSize.isPresent() && !startIndex.isPresent())
				return false;
			else
				// It's better to let clients requesting mismatched options know they goofed
				// than to try and guess their intent.
				throw new IllegalArgumentException(String
						.format("Mismatched paging arguments: pageSize='%s', startIndex='%s'", pageSize, startIndex));
		}

		/*
		 * @return Returns the pageSize as an integer. Note: the pageSize must exist at
		 * this point, otherwise paging would not have been requested.
		 */
		public int getPageSize() {
			if (!isPagingRequested())
				throw new BadCodeMonkeyException();
			return pageSize.get();
		}

		/*
		 * @return Returns the startIndex as an integer. If the startIndex is not set,
		 * return 0.
		 */
		public int getStartIndex() {
			if (!isPagingRequested())
				throw new BadCodeMonkeyException();
			if (startIndex.isPresent()) {
				return startIndex.get();
			}
			return 0;
		}

		/*
		 * @return Returns the serverBase.
		 */
		public String getServerBase() {
			return serverBase;
		}
	}
}
