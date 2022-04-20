package com.example.grpc.client.grpcclient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import java.nio.file.Files;

import com.example.grpc.server.grpcserver.PingRequest;
import com.example.grpc.server.grpcserver.PongResponse;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse.File;
import com.example.grpc.server.grpcserver.PingPongServiceGrpc;
import com.example.grpc.server.grpcserver.MatrixRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import com.example.grpc.client.grpcclient.storage.StorageService;
import com.example.grpc.server.grpcserver.MatrixReply;
import com.example.grpc.server.grpcserver.MatrixServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
// import io.grpc.internal.Stream;
import net.devh.boot.grpc.client.inject.GrpcClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

///////////////// CHANGE UNNEEDED IMPORTS
///////////////////////////////////////////////////////////////////////////////////

@Service
public class GRPCClientService {

	private final StorageService storageService;

	String[] internalIPAddresses = new String[] {
			"localhost",
			"10.128.0.",
			"10.128.0.",
			"10.128.0.",
			"10.128.0.",
			"10.128.0.",
			"10.128.0.",
			"10.128.0." };

	@Autowired
	public GRPCClientService(StorageService storageService) {
		this.storageService = storageService;
	}

	public String ping() {
		ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 9090)
				.usePlaintext()
				.build();
		PingPongServiceGrpc.PingPongServiceBlockingStub stub = PingPongServiceGrpc.newBlockingStub(channel);
		PongResponse helloResponse = stub.ping(PingRequest.newBuilder()
				.setPing("")
				.build());
		channel.shutdown();
		return helloResponse.getPong();
	}

	public String add() {
		ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 9090)
				.usePlaintext()
				.build();
		MatrixServiceGrpc.MatrixServiceBlockingStub stub = MatrixServiceGrpc.newBlockingStub(channel);
		MatrixReply A = stub.addBlock(MatrixRequest.newBuilder()
				.setA00(1)
				.setA01(2)
				.setA10(5)
				.setA11(6)
				.setB00(1)
				.setB01(2)
				.setB10(5)
				.setB11(6)
				.build());
		String resp = A.getC00() + " " + A.getC01() + "<br>" + A.getC10() + " " + A.getC11() + "\n";
		return resp;
	}

	public String mult() {
		ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 9090)
				.usePlaintext()
				.build();
		MatrixServiceGrpc.MatrixServiceBlockingStub stub = MatrixServiceGrpc.newBlockingStub(channel);
		MatrixReply A = stub.multiplyBlock(MatrixRequest.newBuilder()
				.setA00(1)
				.setA01(2)
				.setA10(5)
				.setA11(6)
				.setB00(1)
				.setB01(2)
				.setB10(5)
				.setB11(6)
				.build());
		String resp = A.getC00() + " " + A.getC01() + "<br>" + A.getC10() + " " + A.getC11() + "\n";
		return resp;
	}

	public int[][] extractMatrix(int index) {
		Stream<Path> uploadedFiles = storageService.loadAll();
        Path[] files = uploadedFiles.toArray(Path[]::new);
		
		
		try {
			List<String> element = Files.readAllLines(storageService.load(files[index].toString()), StandardCharsets.US_ASCII);
			int matrixLength = elements.size();
			int[][] matrix = new int[matrixLength][matrixLength];

			int iteration = 0;
			while(iteration<element.length){
				String[] matrixRowString = element[iteration].split(" ");
				int[] matrixRow = new int[matrixRowString.length];
				for (String e : matrixRowString){
					matrixRow[iteration] = Integer.parseInt(e);
				}
				matrix[iteration]=matrixRow[iteration];
				iteration=iteration+1;
			}
			return matrix;
        } catch (IOException e) {
            System.out.println("ERROR");
            e.printStackTrace();
        }
		return null;
	}

	public String view(int[][] matrix) {
		String result = "";

		for (int[] matrixRow : matrix) {
			for (Integer e : matrixRow) {
				result = result + e + " ";
			}
			result = result + "<br>";
		}
		result = result + "<br>";

		return result;
	}
}
