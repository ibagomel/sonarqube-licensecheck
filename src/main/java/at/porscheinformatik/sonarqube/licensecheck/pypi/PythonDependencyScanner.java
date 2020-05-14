package at.porscheinformatik.sonarqube.licensecheck.pypi;

import at.porscheinformatik.sonarqube.licensecheck.Dependency;
import at.porscheinformatik.sonarqube.licensecheck.interfaces.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class PythonDependencyScanner implements Scanner
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PythonDependencyScanner.class);

    private String pythonEnvironmentPath;

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
            (file, s) -> s.endsWith(".dist-info") && file.isDirectory());
        return Arrays.stream(packageDirectories)
            .map(File::getAbsoluteFile)
            .map(this::processPackageDirectory)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private Dependency processPackageDirectory(File directory)
    {
        File metadataFile = new File(directory, "METADATA");
        LOGGER.info("Processing metadata file " + metadataFile.getAbsolutePath());
        if (!metadataFile.exists() || !metadataFile.isFile()) {
            LOGGER.info("No metadata file!");
            return null;
        }
        try
        {
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
        String license = null;
        java.util.Scanner fileScanner = new java.util.Scanner(metadataFile);
        while (fileScanner.hasNextLine()) {
            String nextLine = fileScanner.nextLine();
            name = getValueIfPresent(nextLine, "Name:", name);
            version = getValueIfPresent(nextLine, "Version:", version);
            license = getValueIfPresent(nextLine, "License:", license);
        }
        fileScanner.close();
        return new Dependency(name, version, license);
    }

    private String getValueIfPresent(String line, String prefix, String oldValue)
    {
        if (line.startsWith(prefix))
        {
            return line.substring(prefix.length()).trim();
        }
        return oldValue;
    }
}
