package at.porscheinformatik.sonarqube.licensecheck.pypi;

import at.porscheinformatik.sonarqube.licensecheck.Dependency;
import at.porscheinformatik.sonarqube.licensecheck.interfaces.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class PythonDependencyScanner implements Scanner
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PythonDependencyScanner.class);

    private final String pythonEnvironmentPath;

    public PythonDependencyScanner(String pythonEnvironmentPath)
    {
        LOGGER.info("Python environment template: " + pythonEnvironmentPath);
        this.pythonEnvironmentPath = pythonEnvironmentPath;
    }

    @Override
    public List<Dependency> scan(File venvRoot)
    {
        if (pythonEnvironmentPath == null) {
            LOGGER.info("Python environment is not specified. Not scanning!");
            return Collections.emptyList();
        }
        LOGGER.info("Scanning for environment in " + pythonEnvironmentPath);
        File[] packageDirectories = new File(pythonEnvironmentPath).listFiles    (
            (file, s) -> (s.endsWith(".dist-info") || s.endsWith(".egg-info")) && file.isDirectory());
        return Arrays.stream(packageDirectories)
            .map(File::getAbsoluteFile)
            .map(this::processPackageDirectory)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private Dependency processPackageDirectory(File directory)
    {
        File metadataFiles[] = new File[]
            {
            new File(directory, "METADATA"),
            new File(directory, "PKG-INFO"),
        };
        return Arrays.stream(metadataFiles)
                .map(this::processMetadataFile)
                .filter(Objects::nonNull)
                .findFirst().orElseGet(() -> null);
    }

    private Dependency processMetadataFile(File metadataFile)
    {
        LOGGER.info("Processing metadata file " + metadataFile.getAbsolutePath());
        if (!metadataFile.exists() || !metadataFile.isFile()) {
            LOGGER.info("No metadata file!");
            return null;
        }
        try {
            return metadataToDependency(metadataFile);
        } catch (IOException ignore) {
            LOGGER.info("Exception, while reading metadata");
            return null;
        }
    }

    private Dependency metadataToDependency(File metadataFile) throws IOException
    {
        String name = null;
        String version = null;
        String metadataLicense = null;
        Set<String> classifierLicenses = new TreeSet<>(String::compareToIgnoreCase);
        java.util.Scanner fileScanner = new java.util.Scanner(metadataFile);
        while (fileScanner.hasNextLine()) {
            String nextLine = fileScanner.nextLine();
            name = getValueIfPresent(nextLine, "Name:", name);
            version = getValueIfPresent(nextLine, "Version:", version);
            metadataLicense = getValueIfPresent(nextLine, "License:", metadataLicense);
            String classifierLicense = getValueIfPresent(nextLine, "Classifier: License ::");
            if (classifierLicense != null)
            {
                String preparedClassifierLicense = prepareClassifierLicense(classifierLicense);
                if (preparedClassifierLicense != null)
                {
                    classifierLicenses.add(preparedClassifierLicense);
                }
            }
        }
        fileScanner.close();
        if (name == null)
        {
            LOGGER.warn("Invalid metadata file at " + metadataFile.getAbsolutePath());
            return null;
        }
        Dependency dependency = new Dependency(name, cleanNull(version), prepareLicenses(classifierLicenses, metadataLicense));
        LOGGER.info("Found dependency " + dependency);
        return dependency;
    }

    private static String cleanNull(String value)
    {
        return value == null ? " " : value;
    }

    private static String prepareLicenses(Collection<String> licenses, String metadataLicense) {
        if (metadataLicense != null && !metadataLicense.isEmpty() && !metadataLicense.equals("UNKNOWN")) {
            return metadataLicense;
        }
        return String.join(", ", licenses);
    }

    private static String prepareClassifierLicense(String license)
    {
        String[] licenses = license.split("::");
        String lastLicense = licenses[licenses.length - 1].trim();
        if ("OSI Approved".equals(lastLicense))
        {
            return null;
        }
        return lastLicense;
    }

    private static String getValueIfPresent(String line, String prefix)
    {
        return getValueIfPresent(line, prefix, null);
    }

    private static String getValueIfPresent(String line, String prefix, String oldValue)
    {
        if (line.startsWith(prefix))
        {
            return line.substring(prefix.length()).trim();
        }
        return oldValue;
    }
}
