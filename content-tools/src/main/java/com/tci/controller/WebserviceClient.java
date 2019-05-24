package com.tci.controller;

import java.io.File;
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tci.beans.Sessao;

@Service
public class WebserviceClient {
	private File recebidos = new File("/kodak/recebidos");
	private File enviados = new File("/kodak/enviados");
	private File rejeitados = new File("/kodak/rejeitados");
	
	
	final String COMPANY_HEADER_TAG_NAME = "X-Company-Token";
	
	@Autowired
	private Sessao sessao;
	private static final Logger LOGGER = LogManager.getLogger(WebserviceClient.class);
	@Autowired
	private ObjectMapper mapper;
	private RestTemplate getRestTemplate() {
		RestTemplate restTemplate = new RestTemplate();
		restTemplate.getInterceptors().add(new ClientHttpRequestInterceptor() {
			@Override
			public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)throws IOException {
				String token=sessao.getLoginToken();
				if (token!=null && !token.trim().isEmpty() ) {
					//System.out.println("token-->" + token);
					request.getHeaders().set("Authorization",token);
					request.getHeaders().set(COMPANY_HEADER_TAG_NAME,sessao.getCompanyToken());
				}

				return execution.execute(request, body);
			}
		});
		return restTemplate;
	}
	private String getRoot() {
		return String.format("%s%s", sessao.getUniprofUrl(),"");
	}
	public void logar(String login,String senha) throws Exception {
		String post=String.format("{\"email\": \"%s\",\"password\": \"%s\"}", login,senha) ;
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<String> entity = new HttpEntity<String>(post,headers);
		Object result = getRestTemplate().postForObject(getRoot()+"/api/auth", entity, String.class);
		LOGGER.info("RESPOSTA DO LOGIN --> " + result.toString());
		JsonNode jsonNode = mapper.readTree(result.toString());
		String token = jsonNode.get("token").asText();
		sessao.setLoginToken(token);
		companyToken();
	}
	//https://stackoverflow.com/questions/6349421/how-to-use-jackson-to-deserialise-an-array-of-objects
	private void companyToken() throws Exception {
		ResponseEntity<String> response = getRestTemplate().exchange(
				getRoot()+"/api/companies",
				  HttpMethod.GET,
				  null,
				  new ParameterizedTypeReference<String>(){});
		String result = response.getBody();
		System.out.println(result);
		JsonNode arrNode = mapper.readTree(result.toString());
		if (arrNode.isArray()) {
		    for (final JsonNode objNode : arrNode) {
		    	sessao.setCompanyToken(objNode.get("token").asText());
		    	break;
		    }
		}
		
	}
	public void fecharCarrinho() throws Exception {
		String post=String.format("{\"lotIds\":[\"%s\"]}", sessao.getLoteId()) ;
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<String> entity = new HttpEntity<String>(post,headers);
		Object result = getRestTemplate().postForObject(getRoot()+"/api/carts", entity, String.class);
		LOGGER.info("RESPOSTA DO FECHAR CARRINHO --> " + result.toString());
		JsonNode jsonNode = mapper.readTree(result.toString());
		LOGGER.info(jsonNode.asText());
	}
	/*
	public void listServices() throws Exception {
		ResponseEntity<String> response = getRestTemplate().exchange(
				getRoot()+"/api/services",
				  HttpMethod.GET,
				  null,
				  new ParameterizedTypeReference<String>(){});
		String employees = response.getBody();
		System.out.println(employees);
		//44
	}
	*/
	public void criarLote(int servico,String nome) throws Exception{
		String post=String.format("{\"serviceId\": \"%d\",\"name\":\"%s\",\"description\": \"%s\"}", servico,nome,nome + " descrição") ;
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		HttpEntity<String> entity = new HttpEntity<String>(post,headers);
		Object result = getRestTemplate().postForObject(getRoot()+"/api/lots", entity, String.class);
		LOGGER.info("RESPOSTA DO CRIAR LOTE --> " + result.toString());
		JsonNode jsonNode = mapper.readTree(result.toString());
		String id = jsonNode.get("id").asText();
		sessao.setLoteId(id);
	}
	
	private static final String TIME_ZONE = "America/Sao_Paulo";
	//https://www.baeldung.com/spring-rest-template-multipart-upload
	@Scheduled(cron = "${cron}" , zone = TIME_ZONE)
	public void enviarArquivo() {
		File file=null;
		try {
		if(sessao.isEnviarArquivos()) {
			sessao.setEnviarArquivos(false);
			if(!enviados.exists())
				enviados.mkdirs();
			File[] files = recebidos.listFiles();
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.MULTIPART_FORM_DATA);
			LinkedMultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
	        
			for(File in: files) {
				if(!in.isDirectory()) {
					file=in;
					map.add("file", new FileSystemResource(in));
					map.add("filename", in.getName());
			        
			        HttpEntity<LinkedMultiValueMap<String, Object>> requestEntity = new HttpEntity<>(map, headers);
			        
			        ResponseEntity<String> response=getRestTemplate().exchange(getRoot()+"/api/lots/"+sessao.getLoteId()+"/documents", HttpMethod.POST, requestEntity, String.class);
			        LOGGER.info("response status: " + response.getStatusCode());
			        LOGGER.info("response body: " + response.getBody());
			        in.renameTo(new File(enviados,in.getName()));
				}
			}
		}else
			LOGGER.info("SEM AUTENTICAÇÃO, LOTE NÃO INFORMADO OU AGUARDANDO ENVIO DE IMAGENS");
		}catch (HttpStatusCodeException  e) {
			LOGGER.info("response status: " + e.getStatusCode());
	        LOGGER.info("response body: " + e.getResponseBodyAsString());
	        file.renameTo(new File(rejeitados,file.getName()));
		}catch (Exception e) {
			LOGGER.error("ERRO AO ENVIAR IMAGENS NO LOTE " + sessao.getLoteId() + " " + e.getMessage());
			e.printStackTrace();
		}finally {
			sessao.setEnviarArquivos(true);
		}
	}
}
