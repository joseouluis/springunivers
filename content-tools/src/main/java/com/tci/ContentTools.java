package com.tci;

import javax.swing.UIManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import com.tci.desktop.Desktop;

@SpringBootApplication
public class ContentTools {
	private static final Logger LOGGER = LogManager.getLogger(ContentTools.class);
	public static final String APP_PATH = System.getProperty("user.dir");
	private static ConfigurableApplicationContext contexto;
	public static void main(String[] args) {
		System.setProperty("app.path", APP_PATH);
		try {
			String lf = UIManager.getSystemLookAndFeelClassName();
			UIManager.setLookAndFeel(lf);
			SpringApplicationBuilder builder = new SpringApplicationBuilder(ContentTools.class);
			builder.headless(false);
			contexto = builder.run(args);
			Desktop main = getBean(Desktop.class);
			main.exibir();
			LOGGER.info("Bem vindo");
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
		}
	}
	public static <T> T getBean(Class classe) {
		return (T) contexto.getBean(classe);
	}
}