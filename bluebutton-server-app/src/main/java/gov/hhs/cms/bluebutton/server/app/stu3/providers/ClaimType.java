package gov.hhs.cms.bluebutton.server.app.stu3.providers;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Function;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.metamodel.PluralAttribute;
import javax.persistence.metamodel.SingularAttribute;

import org.hl7.fhir.dstu3.model.ExplanationOfBenefit;

import gov.hhs.cms.bluebutton.data.model.rif.CarrierClaim;
import gov.hhs.cms.bluebutton.data.model.rif.CarrierClaim_;

/**
 * Enumerates the various Blue Button claim types that are supported by
 * {@link ExplanationOfBenefitResourceProvider}.
 */
enum ClaimType {
	CARRIER(CarrierClaim.class, CarrierClaim_.claimId, CarrierClaimTransformer::transform, CarrierClaim_.lines);

	private final Class<?> entityClass;
	private final SingularAttribute<?, ?> entityIdAttribute;
	private final Function<Object, ExplanationOfBenefit> transformer;
	private final Collection<PluralAttribute<?, ?, ?>> entityLazyAttributes;

	/**
	 * Enum constant constructor.
	 * 
	 * @param entityClass
	 *            the value to use for {@link #getEntityClass()}
	 * @param entityIdAttribute
	 *            the value to use for {@link #getEntityIdAttribute()}
	 * @param transformer
	 *            the value to use for {@link #getTransformer()}
	 * @param entityLazyAttributes
	 *            the value to use for {@link #getEntityLazyAttributes()}
	 */
	private ClaimType(Class<?> entityClass, SingularAttribute<?, ?> entityIdAttribute,
			Function<Object, ExplanationOfBenefit> transformer, PluralAttribute<?, ?, ?>... entityLazyAttributes) {
		this.entityClass = entityClass;
		this.entityIdAttribute = entityIdAttribute;
		this.transformer = transformer;
		this.entityLazyAttributes = Collections.unmodifiableCollection(Arrays.asList(entityLazyAttributes));
	}

	/**
	 * @return the JPA {@link Entity} {@link Class} used to store instances of
	 *         this {@link ClaimType} in the database
	 */
	public Class<?> getEntityClass() {
		return entityClass;
	}

	/**
	 * @return the JPA {@link Entity} field used as the entity's {@link Id}
	 */
	public SingularAttribute<?, ?> getEntityIdAttribute() {
		return entityIdAttribute;
	}

	/**
	 * @return the {@link Function} to use to transform the JPA {@link Entity}
	 *         instances into FHIR {@link ExplanationOfBenefit} instances
	 */
	public Function<Object, ExplanationOfBenefit> getTransformer() {
		return transformer;
	}

	/**
	 * @return the {@link PluralAttribute}s in the JPA {@link Entity} that are
	 *         {@link FetchType#LAZY}
	 */
	public Collection<PluralAttribute<?, ?, ?>> getEntityLazyAttributes() {
		return entityLazyAttributes;
	}

	/**
	 * @param claimTypeText
	 *            the lower-cased {@link ClaimType#name()} value to parse back
	 *            into a {@link ClaimType}
	 * @return the {@link ClaimType} represented by the specified {@link String}
	 */
	public static Optional<ClaimType> parse(String claimTypeText) {
		for (ClaimType claimType : ClaimType.values())
			if (claimType.name().toLowerCase().equals(claimTypeText))
				return Optional.of(claimType);
		return Optional.empty();
	}
}