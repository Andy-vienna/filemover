package org.andy.filemover;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.Collectors;

public class FileMover {

	private final Logger logger = Logger.getLogger(FileMover.class.getName());

	private final Properties config;
	private final Set<String> fileExtensions;
	private final Path sourceDir;
	private final Path targetDir;

	// ###################################################################################################################################################
	// ###################################################################################################################################################

	public static void main(String[] args) {
		try {
			new FileMover().startWatching();
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	public FileMover() throws IOException {
		setupLogger();
		config = loadConfig();
		fileExtensions = loadExtensions("watch.extension");
		sourceDir = Paths.get(config.getProperty("watch.path"));
		targetDir = Paths.get(config.getProperty("target.path"));
	}

	// ###################################################################################################################################################
	// ###################################################################################################################################################

	private void startWatching() throws IOException, InterruptedException {
		WatchService watchService = FileSystems.getDefault().newWatchService();
		sourceDir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
		logger.info("Überwache Verzeichnis: " + sourceDir);

		while (true) {
			WatchKey key = watchService.take();

			for (WatchEvent<?> event : key.pollEvents()) {
				if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
					continue;
				}

				@SuppressWarnings("unchecked")
				WatchEvent<Path> ev = (WatchEvent<Path>) event;
				Path fileName = ev.context();
				String fileNameLower = fileName.toString().toLowerCase();

				if (fileExtensions.stream().anyMatch(fileNameLower::endsWith)) {
					Path sourcePath = sourceDir.resolve(fileName);
					Path targetPath = targetDir.resolve(fileName);

					while (isLocked(sourcePath)) {
						Thread.sleep(500);
					}

					try {
						Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
						logger.info("Datei verschoben: " + fileName);
					} catch (IOException e) {
						logger.severe("Fehler beim Verschieben: " + e.getMessage());
					}
				}
			}

			if (!key.reset()) {
				logger.info("Beobachtung beendet.");
				break;
			}
		}
	}

	private boolean isLocked(Path path) {
		try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
			channel.size();
			return false;
		} catch (IOException e) {
			return true;
		}
	}

	private Set<String> loadExtensions(String key) {
		String extStr = config.getProperty(key);
		if (extStr == null || extStr.isEmpty()) {
			return Set.of();
		}

		return Arrays.stream(extStr.split(","))
				.map(String::trim)
				.map(String::toLowerCase)
				.collect(Collectors.toSet());
	}

	private Properties loadConfig() throws IOException {
		Properties props = new Properties();

		String jarPath = FileMover.class.getProtectionDomain().getCodeSource().getLocation().getPath();
		if (jarPath.startsWith("/")) {
			jarPath = jarPath.substring(1);
		}
		Path jarDir = Paths.get(jarPath).getParent();
		Path configPath = jarDir.resolve("config.properties");

		try (InputStream in = Files.newInputStream(configPath)) {
			props.load(in);
		}

		return props;
	}

	private void setupLogger() throws IOException {
		LogManager.getLogManager().reset();
		Logger rootLogger = Logger.getLogger("");
		ConsoleHandler consoleHandler = new ConsoleHandler();
		consoleHandler.setLevel(Level.INFO);
		rootLogger.addHandler(consoleHandler);

		FileHandler fileHandler = new FileHandler("filemover.log", true);
		fileHandler.setLevel(Level.ALL);
		fileHandler.setFormatter(new SimpleFormatter());
		rootLogger.addHandler(fileHandler);
	}
}

