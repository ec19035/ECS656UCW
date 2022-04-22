package com.example.grpc.client.grpcclient;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;

@RestController
public class PingPongEndpoint {

	GRPCClientService grpcClientService;

	@Autowired
	public PingPongEndpoint(GRPCClientService grpcClientService) {
		this.grpcClientService = grpcClientService;
	}

	@GetMapping("/ping")
	public String ping() {
		return grpcClientService.ping();
	}

	//////////////////////////////////////////////////////////////////////////////////
	@GetMapping("/viewpage")
	public String view() {
		List<List<Integer>> uploaded1Matrix = grpcClientService.getMatrix(0);
		List<List<Integer>> uploaded2Matrix = grpcClientService.getMatrix(1);

		String result = grpcClientService.view(uploaded1Matrix);
		result = result + grpcClientService.view(uploaded2Matrix);
		return result;
	}

	///       VIEW PAGE AND UPLOAD MATRIX
	/////////////////////////////////////////////////////////////////////////////////

	@GetMapping("/add")
	public String add() {
		return grpcClientService.add();
	}

	@GetMapping("/mult")
	public String mult() {
		return grpcClientService.mult();
	}
}
