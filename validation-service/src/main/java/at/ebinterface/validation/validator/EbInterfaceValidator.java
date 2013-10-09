package at.ebinterface.validation.validator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.util.JAXBResult;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.apache.xerces.util.XMLCatalogResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import at.ebinterface.validation.digitalsignatures.MOASignatureValidator;
import at.ebinterface.validation.exception.NamespaceUnknownException;
import at.ebinterface.validation.parser.CustomParser;
import at.ebinterface.validation.validator.jaxb.Result;
import at.gv.egovernment.moa.spss.api.common.SignerInfo;
import at.gv.egovernment.moa.spss.api.xmlverify.VerifyXMLSignatureResponse;

/**
 * This class validates a given ebInterface XML instance against a schematron
 * file (already transformed to .xsl)
 * 
 * @author pl
 * 
 */
public class EbInterfaceValidator {

	/** Validators for checking XML instances against the ebinterface schemas */
	private static Validator ebInterface3p0Validator;

	private static Validator ebInterface3p02Validator;

	private static Validator ebInterface4p0Validator;

	/** Transformer factory */
	private static TransformerFactory tFactory;

	/** Interim transformer */
	private static Transformer interimTransformer;

	/** Transformer for generating the final report from schematron */
	private static Transformer reportTransformer;

	/** ebInterface XSLT transformers */
	private static Transformer ebInterface3p0Transformer;
	private static Transformer ebInterface3p02Transformer;
	private static Transformer ebInterface4p0Transformer;

	/** JAXBContext for generating the result */
	private static JAXBContext jaxb;

	/**
	 * Initialize the validator
	 */
	static {

		SchemaFactory factory = SchemaFactory
				.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

		String catalog = EbInterfaceValidator.class.getResource(
				"/schemas/catalog.xml").getFile();
		XMLCatalogResolver resolver = new XMLCatalogResolver(
				new String[] { catalog });
		factory.setResourceResolver(resolver);

		// Load a WXS schema, represented by a Schema instance.
		Source ebInterface3p0schemaFile = new StreamSource(
				EbInterfaceValidator.class
						.getResourceAsStream("/ebinterface/ebInterface3p0.xsd"));
		Source ebInterface3p02schemaFile = new StreamSource(
				EbInterfaceValidator.class
						.getResourceAsStream("/ebinterface/ebInterface3p02.xsd"));
		Source ebInterface4p0schemaFile = new StreamSource(
				EbInterfaceValidator.class
						.getResourceAsStream("/ebinterface/ebInterface4p0.xsd"));
		try {
			Schema schema3p0 = factory.newSchema(ebInterface3p0schemaFile);
			Schema schema3p02 = factory.newSchema(ebInterface3p02schemaFile);
			Schema schema4p0 = factory.newSchema(ebInterface4p0schemaFile);

			// Create a Validator object, which can be used to validate
			// an instance document.
			ebInterface3p0Validator = schema3p0.newValidator();
			ebInterface3p02Validator = schema3p02.newValidator();
			ebInterface4p0Validator = schema4p0.newValidator();

		} catch (Exception e) {
			new RuntimeException(e);
		}

		// Get a transformer factory
		tFactory = TransformerFactory.newInstance();

		/*
		 * Initialize the XSLT Transformer for generating the interim XSLTs
		 * based on the implementation
		 */
		final String schematronImplUrl = EbInterfaceValidator.class
				.getResource(
						"/schematron-resources/iso-schematron-xslt2/iso_svrl_for_xslt2.xsl")
				.toString();
		final String reportUrl = EbInterfaceValidator.class.getResource(
				"/schematron-resources/custom/report.xsl").toString();
		try {
			// Schematron transformers

			// Initialize the interim transformer
			interimTransformer = tFactory.newTransformer(new StreamSource(
					schematronImplUrl));
			// Initialize the final transformer
			reportTransformer = tFactory.newTransformer(new StreamSource(
					reportUrl));

			// Transformers for ebInterface visualization
			String styleSheetFile = EbInterfaceValidator.class.getResource(
					"/stylesheets/ebInterface-3.0.xslt").toString();
			ebInterface3p0Transformer = tFactory
					.newTransformer(new StreamSource(styleSheetFile));

			styleSheetFile = EbInterfaceValidator.class.getResource(
					"/stylesheets/ebInterface-3.0.2.xslt").toString();
			ebInterface3p02Transformer = tFactory
					.newTransformer(new StreamSource(styleSheetFile));

			styleSheetFile = EbInterfaceValidator.class.getResource(
					"/stylesheets/ebInterface-4.0.xslt").toString();
			ebInterface4p0Transformer = tFactory
					.newTransformer(new StreamSource(styleSheetFile));

		} catch (TransformerConfigurationException e) {
			throw new RuntimeException(e);
		}

		// JAXB context
		try {
			jaxb = JAXBContext.newInstance(Result.class);
		} catch (JAXBException e) {
			throw new RuntimeException(e);
		}

	}

	/**
	 * Validate the XML instance input stream
	 * 
	 * @param schematronFileReference
	 *            Which schematron file to use
	 * @param inputStream
	 *            The XML input stream
	 */
	public ValidationResult validateXMLInstanceAgainstSchema(byte[] uploadedData) {

		ValidationResult result = new ValidationResult();

		// Step 1 - determine the correct ebInterface version
		EbInterfaceVersion version;
		try {
			version = CustomParser.INSTANCE
					.getEbInterfaceDetails(new InputSource(
							new ByteArrayInputStream(uploadedData)));
			result.setDeterminedEbInterfaceVersion(version);
		} catch (NamespaceUnknownException e1) {
			result.setSchemaValidationErrorMessage(e1.getMessage());
			return result;

		}

		// Step 2 - invoke the correct parser for the determined ebInterface
		// version
		try {

			SAXSource saxSource = new SAXSource(new InputSource(
					new ByteArrayInputStream(uploadedData)));

			if (version == EbInterfaceVersion.E3P0) {
				ebInterface3p0Validator.validate(saxSource);
			} else if (version == EbInterfaceVersion.E3P02) {
				ebInterface3p02Validator.validate(saxSource);
			} else {
				ebInterface4p0Validator.validate(saxSource);
			}

		} catch (SAXException e) {
			result.setSchemaValidationErrorMessage(e.getMessage());
		} catch (IOException e) {
			new RuntimeException(e);
		}

		// Step 3 - in case the document is signed, check the signature as well
		try {
			if (version.isSigned()) {
				
				VerifyXMLSignatureResponse verificationResponse = MOASignatureValidator.INSTANCE.validate(uploadedData, version.getSignatureNamespacePrefix());
												
				//Set the result of the certificate check
				if (verificationResponse.getCertificateCheck().getCode() == 0) {
					result.setCertificateOK(true);
				}
				else {
					result.setCertificateOK(false);
				}
				
				//Set the result of the signature check
				if (verificationResponse.getSignatureCheck().getCode() == 0) {
					result.setSignatureOK(true);
				}
				else {
					result.setSignatureOK(false);
				}
				
				//Set the certificate details
			    // Besondere Eigenschaften des Signatorzertifikats
			    SignerInfo signerInfo = verificationResponse.getSignerInfo();
			    if (signerInfo != null) {
			    	result.setCertificateIssuer(signerInfo.getSignerCertificate().getIssuerDN().toString());
			    	result.setCertificateSubject(signerInfo.getSignerCertificate().getSubjectDN().toString());
			    	result.setCertificateSerialNumber(signerInfo.getSignerCertificate().getSerialNumber().toString());
			    	
				    result.setCertificateOfSignatorQualified(signerInfo.isQualifiedCertificate());
				    result.setCertificateOfSignatorFromAPublicAuthority(signerInfo.isPublicAuthority());
				    			    	
			    }
			    
			}			
			
		} catch (Exception e) {
			result.setCertificateOK(false);
			result.setSignatureOK(false);
		}

		return result;
	}

	/**
	 * Apply the correct stylesheet and transform the input
	 * 
	 * @param uploadedData
	 * @return
	 */
	public String transformInput(byte[] uploadedData, EbInterfaceVersion version) {

		try {
			final StringWriter sw = new StringWriter();

			if (version == EbInterfaceVersion.E3P0) {
				ebInterface3p0Transformer.transform(new StreamSource(
						new ByteArrayInputStream(uploadedData)),
						new StreamResult(sw));
			} else if (version == EbInterfaceVersion.E3P02) {
				ebInterface3p02Transformer.transform(new StreamSource(
						new ByteArrayInputStream(uploadedData)),
						new StreamResult(sw));
			} else {
				ebInterface4p0Transformer.transform(new StreamSource(
						new ByteArrayInputStream(uploadedData)),
						new StreamResult(sw));
			}
			return sw.toString();

		} catch (Exception e) {
			return "XSLT Transformation konnte nicht ausgeführt werden. Fehler: "
					+ e.getMessage();
		}

	}

	/**
	 * Validate the given XML instance against the given schematron file
	 * 
	 * @param inputStream
	 * @param schematronFileReference
	 */
	public Result validateXMLInstanceAgainstSchematron(byte[] uploadedData,
			String schematronFileReference) {

		try {
			Transformer transformer = getTransformer(schematronFileReference);

			// create a new string writer to hold the output of the validation
			// transformation
			final StringWriter sw = new StringWriter();

			// apply the validating XSLT to the ebinterface document
			transformer.transform(new StreamSource(new ByteArrayInputStream(
					uploadedData)), new StreamResult(sw));

			final JAXBResult jaxbResult = new JAXBResult(jaxb);

			// apply the final transformation
			reportTransformer.transform(
					new StreamSource(new StringReader(sw.toString())),
					jaxbResult);

			return (Result) jaxbResult.getResult();

		} catch (TransformerException e) {
			new RuntimeException(e);
		} catch (JAXBException e) {
			new RuntimeException(e);
		}

		return null;
	}

	/**
	 * Get the right schematron transformer using the url path
	 * 
	 * @param urlPath
	 * @return
	 * @throws TransformerException
	 */
	private Transformer getTransformer(final String urlPath)
			throws TransformerException {

		final StringWriter sw = new StringWriter();

		/* Read the Schematron source */
		final String schematronDocumentUrl = this.getClass()
				.getResource(urlPath).toString();

		interimTransformer.transform(new StreamSource(schematronDocumentUrl),
				new StreamResult(sw));

		return tFactory.newTransformer(new StreamSource(new StringReader(sw
				.toString())));
	}

}