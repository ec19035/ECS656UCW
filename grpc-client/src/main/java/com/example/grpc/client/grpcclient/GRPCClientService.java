package com.example.grpc.client.grpcclient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.lang.Math;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
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
import net.devh.boot.grpc.client.inject.GrpcClient;
import com.google.common.util.concurrent.ListenableFuture;

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
			"10.128.0.3",
			"10.128.0.4",
			"10.128.0.5",
			"10.128.0.6",
			"10.128.0.7",
			"10.128.0.8",
			"10.128.0.9",
			"10.128.0.10" };

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

	public int[][] mult(int[][] uploaded1Matrix, int[][] uploaded2Matrix, int setDeadline) {
		int[][] firstMatrix = uploaded1Matrix;
		int[][] secondMatrix = uploaded2Matrix;

		List<ManagedChannel> allChannels = new ArrayList<>();
		List<MatrixServiceGrpc.MatrixServiceFutureStub> allStubs = new ArrayList<>();

		int whileIter = 0;
		while (whileIter < internalIPAddresses.length) {
			allChannels
					.add(ManagedChannelBuilder.forAddress(internalIPAddresses[whileIter], 9090).usePlaintext().build());
			whileIter = whileIter + 1;
		}

		whileIter = 0;
		while (whileIter < allChannels.size()) {
			allStubs.add(MatrixServiceGrpc.newFutureStub(allChannels.get(whileIter)));
			whileIter = whileIter + 1;
		}

		CountDownLatch countdownLatch = new CountDownLatch(1);
		Random randomInt = new Random();
		long footPrint = 0;

		long timerStart = System.nanoTime();

		MatrixServiceGrpc.MatrixServiceFutureStub sltStub = allStubs.get(randomInt.nextInt(8));

		MatrixRequest mtxRequest = MatrixRequest.newBuilder()
				.setA00(firstMatrix[0][0])
				.setB00(secondMatrix[secondMatrix.length - 1][secondMatrix.length - 1])
				.build();
		ListenableFuture<MatrixReply> lstnblFuture = sltStub.multiplyBlock(mtxRequest);
		MatrixReply mtxReply = null;

		try {
			mtxReply = lstnblFuture.get();
			countdownLatch.countDown();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}

		try {
			countdownLatch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		long timerEnd = System.nanoTime();

		footPrint = timerEnd - timerStart;

		System.out.println("The Footprint is: " + footPrint);

		double blockCallNumber = (double) Math.pow(firstMatrix.length, 2);
		System.out.println("Number of Block that have been Called: " + blockCallNumber);

		System.out.println("The Deadline is: " + setDeadline);

		double executionTime = (footPrint * blockCallNumber) / 1000000000;
		System.out.println("The Execution Time is: " + executionTime);

		int totalServerNumber = (int) (executionTime / setDeadline);

		if (totalServerNumber < 1) {
			totalServerNumber = 1;
		}
		if (totalServerNumber > 8) {
			totalServerNumber = 8;
		}
		System.out.println("The total Number of Servers is: " + totalServerNumber);

		int idx = 0;

		int[][] rsltMatrix = new int[firstMatrix.length][firstMatrix.length];

		for (int i = 0; i < firstMatrix.length; i++) { // row
			for (int j = 0; j < firstMatrix.length; j++) { // col
				for (int k = 0; k < firstMatrix.length; k++) {
					ListenableFuture<MatrixReply> lstnblFuture1=allStubs.get(idx).multiplyBlock(MatrixRequest.newBuilder().setA00(firstMatrix[i][k]).setB00(secondMatrix[k][j]).build());

					MatrixReply mtxReply1 = null;
					try {
						mtxReply1 = lstnblFuture1.get();
						countdownLatch.countDown();
					} catch (InterruptedException e) {
						e.printStackTrace();
					} catch (ExecutionException e) {
						e.printStackTrace();
					}
					
					if (idx == totalServerNumber - 1) {
							idx = 0;
					} else {
							idx = idx + 1;
					}

					ListenableFuture<MatrixReply> lstnblFuture2=allStubs.get(idx).addBlock(MatrixRequest.newBuilder().setA00(rsltMatrix[i][j]).setB00(mtxReply1.getC00()).build());
					
					MatrixReply mtxReply2 = null;
					try {
						mtxReply2 = lstnblFuture2.get();
						countdownLatch.countDown();
					} catch (InterruptedException e) {
						e.printStackTrace();
					} catch (ExecutionException e) {
						e.printStackTrace();
					}

					rsltMatrix[i][j] = mtxReply2.getC00();

					if (idx == totalServerNumber - 1) {
							idx = 0;
					} else {
							idx = idx + 1;
					}
				}
			}
		}

		// int[][] rsltMatrix = multfinaliseMatrix(firstMatrix, secondMatrix, totalServerNumber, allStubs, idx);

		whileIter = 0;
		while (whileIter < allChannels.size()) {
			allChannels.get(whileIter).shutdown();
			whileIter = whileIter + 1;
		}

		return rsltMatrix;
	}

	// public static int[][] multfinaliseMatrix(int[][] firstMatrix, int[][] secondMatrix, int totalServerNumber,
	// 		List<MatrixServiceGrpc.MatrixServiceFutureStub> allStubs, int stubIdx) {
	// 	return multMatrix(firstMatrix, secondMatrix, 0, 0, 0, 0, firstMatrix.length, totalServerNumber, allStubs,
	// 			stubIdx);
	// }

	// private static int[][] multMatrix(int[][] firstMatrix, int[][] secondMatrix, int rowfst, int colfst, int rowscd,
	// 		int colscd, int mtxSize, int totalServerNumber, List<MatrixServiceGrpc.MatrixServiceFutureStub> allStubs,
	// 		int stubIdx) {
	// 	int[][] rsltMatrix = new int[mtxSize][mtxSize];
	// 	if (mtxSize == 1) {
	// 		MatrixServiceGrpc.MatrixServiceFutureStub currentStub = allStubs.get(stubIdx);

	// 		MatrixRequest mtxRequest = MatrixRequest.newBuilder()
	// 				.setA00(firstMatrix[rowfst][colfst])
	// 				.setB00(secondMatrix[rowscd][colscd])
	// 				.build();

	// 		ListenableFuture<MatrixReply> lstnblFuture = currentStub.multiplyBlock(mtxRequest);
	// 		MatrixReply mtxReply = null;

	// 		try {
	// 			mtxReply = lstnblFuture.get();
	// 		} catch (InterruptedException e) {
	// 			e.printStackTrace();
	// 		} catch (ExecutionException e) {
	// 			e.printStackTrace();
	// 		}

	// 		rsltMatrix[0][0] = mtxReply.getC00();
	// 	}

	// 	else {
	// 		int newmtxSize = mtxSize / 2;
	// 		// C11
	// 		matrixMultSummation(rsltMatrix,
	// 				multMatrix(firstMatrix, secondMatrix, rowfst, colfst, rowscd, colscd, newmtxSize, totalServerNumber,
	// 						allStubs, stubIdx),
	// 				multMatrix(firstMatrix, secondMatrix, rowfst, colfst + newmtxSize, rowscd + newmtxSize, colscd,
	// 						newmtxSize, totalServerNumber, allStubs, stubIdx),
	// 				0, 0, allStubs, stubIdx);
	// 		if (stubIdx == totalServerNumber - 1) {
	// 			stubIdx = 0;
	// 		} else {
	// 			stubIdx = stubIdx + 1;
	// 		}

	// 		// C12
	// 		matrixMultSummation(rsltMatrix,
	// 				multMatrix(firstMatrix, secondMatrix, rowfst, colfst, rowscd, colscd + newmtxSize, newmtxSize,
	// 						totalServerNumber, allStubs, stubIdx),
	// 				multMatrix(firstMatrix, secondMatrix, rowfst, colfst + newmtxSize, rowscd + newmtxSize,
	// 						colscd + newmtxSize, newmtxSize, totalServerNumber, allStubs, stubIdx),
	// 				0, newmtxSize, allStubs, stubIdx);
	// 		if (stubIdx == totalServerNumber - 1) {
	// 			stubIdx = 0;
	// 		} else {
	// 			stubIdx = stubIdx + 1;
	// 		}

	// 		// C21
	// 		matrixMultSummation(rsltMatrix,
	// 				multMatrix(firstMatrix, secondMatrix, rowfst + newmtxSize, colfst, rowscd, colscd, newmtxSize,
	// 						totalServerNumber, allStubs, stubIdx),
	// 				multMatrix(firstMatrix, secondMatrix, rowfst + newmtxSize, colfst + newmtxSize, rowscd + newmtxSize,
	// 						colscd, newmtxSize, totalServerNumber, allStubs, stubIdx),
	// 				newmtxSize, 0, allStubs, stubIdx);
	// 		if (stubIdx == totalServerNumber - 1) {
	// 			stubIdx = 0;
	// 		} else {
	// 			stubIdx = stubIdx + 1;
	// 		}

	// 		// C22
	// 		matrixMultSummation(rsltMatrix,
	// 				multMatrix(firstMatrix, secondMatrix, rowfst + newmtxSize, colfst, rowscd, colscd + newmtxSize,
	// 						newmtxSize, totalServerNumber, allStubs, stubIdx),
	// 				multMatrix(firstMatrix, secondMatrix, rowfst + newmtxSize, colfst + newmtxSize, rowscd + newmtxSize,
	// 						colscd + newmtxSize, newmtxSize, totalServerNumber, allStubs, stubIdx),
	// 				newmtxSize, newmtxSize, allStubs, stubIdx);
	// 		if (stubIdx == totalServerNumber - 1) {
	// 			stubIdx = 0;
	// 		} else {
	// 			stubIdx = stubIdx + 1;
	// 		}
	// 	}
	// 	return rsltMatrix;
	// }

	// private static void matrixMultSummation(int[][] rsltMatrix, int[][] firstMatrix, int[][] secondMatrix, int rowRslt,
	// 		int colRslt, List<MatrixServiceGrpc.MatrixServiceFutureStub> allStubs, int stubIdx) {
	// 	int mtxSizeIter = firstMatrix.length;
	// 	int whileIterRow = 0;
	// 	int whileIterCol = 0;
	// 	while (whileIterRow < mtxSizeIter) {
	// 		while (whileIterCol < mtxSizeIter) {
	// 			MatrixServiceGrpc.MatrixServiceFutureStub currentStub = allStubs.get(stubIdx);
	// 			MatrixRequest mtxRequest = MatrixRequest.newBuilder()
	// 					.setA00(firstMatrix[whileIterRow][whileIterCol])
	// 					.setB00(secondMatrix[whileIterRow][whileIterCol])
	// 					.build();

	// 			ListenableFuture<MatrixReply> lstnblFuture = currentStub.addBlock(mtxRequest);
	// 			MatrixReply mtxReply = null;

	// 			try {
	// 				mtxReply = lstnblFuture.get();
	// 			} catch (InterruptedException e) {
	// 				e.printStackTrace();
	// 			} catch (ExecutionException e) {
	// 				e.printStackTrace();
	// 			}

	// 			rsltMatrix[whileIterRow + rowRslt][whileIterCol + colRslt] = mtxReply.getC00();
	// 			whileIterCol = whileIterCol + 1;
	// 		}
	// 		whileIterRow = whileIterRow + 1;
	// 	}
	// }

	// public String add() {
	// ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 9090)
	// .usePlaintext()
	// .build();
	// MatrixServiceGrpc.MatrixServiceBlockingStub stub =
	// MatrixServiceGrpc.newBlockingStub(channel);
	// MatrixReply A = stub.addBlock(MatrixRequest.newBuilder()
	// .setA00(1)
	// .setA01(2)
	// .setA10(5)
	// .setA11(6)
	// .setB00(1)
	// .setB01(2)
	// .setB10(5)
	// .setB11(6)
	// .build());
	// String resp = A.getC00() + " " + A.getC01() + "<br>" + A.getC10() + " " +
	// A.getC11() + "\n";
	// return resp;
	// }

	// public String mult() {
	// ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 9090)
	// .usePlaintext()
	// .build();
	// MatrixServiceGrpc.MatrixServiceBlockingStub stub =
	// MatrixServiceGrpc.newBlockingStub(channel);
	// MatrixReply A = stub.multiplyBlock(MatrixRequest.newBuilder()
	// .setA00(1)
	// .setA01(2)
	// .setA10(5)
	// .setA11(6)
	// .setB00(1)
	// .setB01(2)
	// .setB10(5)
	// .setB11(6)
	// .build());
	// String resp = A.getC00() + " " + A.getC01() + "<br>" + A.getC10() + " " +
	// A.getC11() + "\n";
	// return resp;
	// }

	public int[][] extractMatrix(int idx) {
		Stream<Path> uploadAllMatrices = storageService.loadAll();
		Path[] files = uploadAllMatrices.toArray(Path[]::new);
		int iter = 0;
		try {
			List<String> matrixElements = Files.readAllLines(storageService.load(files[idx].toString()),
					StandardCharsets.US_ASCII);
			int[][] matrix = new int[matrixElements.size()][matrixElements.size()];
			while (iter < matrixElements.size()) {
				String[] stringMatrixRow = matrixElements.get(iter).split(" ");
				for (int i = 0; i < stringMatrixRow.length; i++) {
					matrix[iter][i] = Integer.parseInt(stringMatrixRow[i]);
				}
				iter = iter + 1;
			}
			return matrix;
		} catch (IOException e) {
			System.out.println("ERROR WHILE READING FILE");
			e.printStackTrace();
		}
		return null;
	}

	public String view(int[][] matrix) {
		String result = "";
		int iter = 0;
		while (iter < matrix.length) {
			for (int i = 0; i < matrix[0].length; i++) {
				result = result + matrix[iter][i] + " ";
			}
			result = result + "<br>";
			iter = iter + 1;
		}
		result = result + "<br>";
		return result;
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//////// ////////
	//////// ADD FUNCTION MATRICES NOT SURE IF IT WORKS ////////
	//////// ////////
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

// 	public int[][] add(int[][] uploaded1Matrix, int[][] uploaded2Matrix, int setDeadline) {
// 		int[][] firstMatrix = uploaded1Matrix;
// 		int[][] secondMatrix = uploaded2Matrix;

// 		List<ManagedChannel> allChannels = new ArrayList<>();
// 		List<MatrixServiceGrpc.MatrixServiceFutureStub> allStubs = new ArrayList<>();

// 		int whileIter = 0;
// 		while (whileIter < internalIPAddresses.length) {
// 			allChannels
// 					.add(ManagedChannelBuilder.forAddress(internalIPAddresses[whileIter], 9090).usePlaintext().build());
// 			whileIter = whileIter + 1;
// 		}

// 		whileIter = 0;
// 		while (whileIter < allChannels.size()) {
// 			allStubs.add(MatrixServiceGrpc.newFutureStub(allChannels.get(whileIter)));
// 			whileIter = whileIter + 1;
// 		}

// 		CountDownLatch countdownLatch = new CountDownLatch(1);
// 		Random randomInt = new Random();
// 		long footPrint = 0;

// 		whileIter = 0;
// 		while (whileIter < 3) {
// 			long timerStart = System.nanoTime();

// 			MatrixServiceGrpc.MatrixServiceFutureStub sltStub = allStubs.get(randomInt.nextInt(8));

// 			MatrixRequest mtxRequest = MatrixRequest.newBuilder()
// 					.setA00(firstMatrix[0][0])
// 					.setB00(secondMatrix[secondMatrix.length - 1][secondMatrix.length - 1])
// 					.build();
// 			ListenableFuture<MatrixReply> lstnblFuture = sltStub.addBlock(mtxRequest);
// 			MatrixReply mtxReply = null;

// 			try {
// 				mtxReply = lstnblFuture.get();
// 				countdownLatch.countDown();
// 			} catch (InterruptedException e) {
// 				e.printStackTrace();
// 			} catch (ExecutionException e) {
// 				e.printStackTrace();
// 			}

// 			try {
// 				countdownLatch.await();
// 			} catch (InterruptedException e) {
// 				e.printStackTrace();
// 			}

// 			long timerEnd = System.nanoTime();
// 			footPrint += (timerEnd - timerStart);
// 			whileIter = whileIter + 1;
// 		}

// 		footPrint = footPrint / 3;
// 		System.out.println("The Footprint is: " + footPrint);

// 		double blockCallNumber = (double) Math.pow(firstMatrix.length, 2);
// 		System.out.println("Number of Block that have been Called: " + blockCallNumber);

// 		System.out.println("The Deadline is: " + setDeadline);

// 		double executionTime = (footPrint * blockCallNumber) / 1000000000;
// 		System.out.println("The Execution Time is: " + executionTime);

// 		int totalServerNumber = (int) (executionTime / setDeadline);

// 		if (totalServerNumber < 1) {
// 			totalServerNumber = 1;
// 		}
// 		if (totalServerNumber > 8) {
// 			totalServerNumber = 8;
// 		}
// 		System.out.println("The total Number of Servers is: " + totalServerNumber);

// 		int idx = 0;
// 		long strTime = System.nanoTime();
// 		int[][] rsltMatrix = addfinaliseMatrix(firstMatrix, secondMatrix, totalServerNumber, allStubs, idx);
// 		long endTime = System.nanoTime();

// 		long timeTotal = (endTime - strTime);
// 		System.out.println("Total time: " + timeTotal / 1000000000);

// 		whileIter = 0;
// 		while (whileIter < allChannels.size()) {
// 			allChannels.get(whileIter).shutdown();
// 			whileIter = whileIter + 1;
// 		}

// 		return rsltMatrix;
// 	}

// 	public static int[][] addfinaliseMatrix(int[][] firstMatrix, int[][] secondMatrix, int totalServerNumber,
// 			List<MatrixServiceGrpc.MatrixServiceFutureStub> allStubs, int stubIdx) {
// 		return multMatrix(firstMatrix, secondMatrix, 0, 0, 0, 0, firstMatrix.length, totalServerNumber, allStubs,
// 				stubIdx);
// 	}

// 	private static int[][] addMatrix(int[][] firstMatrix, int[][] secondMatrix, int rowfst, int colfst, int rowscd,
// 			int colscd, int mtxSize, int totalServerNumber, List<MatrixServiceGrpc.MatrixServiceFutureStub> allStubs,
// 			int stubIdx) {
// 		int[][] rsltMatrix = new int[mtxSize][mtxSize];
// 		if (mtxSize == 1) {
// 			MatrixServiceGrpc.MatrixServiceFutureStub currentStub = allStubs.get(stubIdx);

// 			MatrixRequest mtxRequest = MatrixRequest.newBuilder()
// 					.setA00(firstMatrix[rowfst][colfst])
// 					.setB00(secondMatrix[rowscd][colscd])
// 					.build();

// 			ListenableFuture<MatrixReply> lstnblFuture = currentStub.addBlock(mtxRequest);
// 			MatrixReply mtxReply = null;

// 			try {
// 				mtxReply = lstnblFuture.get();
// 			} catch (InterruptedException e) {
// 				e.printStackTrace();
// 			} catch (ExecutionException e) {
// 				e.printStackTrace();
// 			}

// 			rsltMatrix[0][0] = mtxReply.getC00();
// 		}

// 		else {
// 			int newmtxSize = mtxSize / 2;
// 			// C11
// 			matrixAddSummation(rsltMatrix,
// 					addMatrix(firstMatrix, secondMatrix, rowfst, colfst, rowscd, colscd, newmtxSize, totalServerNumber,
// 							allStubs, stubIdx),
// 					addMatrix(firstMatrix, secondMatrix, rowfst, colfst + newmtxSize, rowscd + newmtxSize, colscd,
// 							newmtxSize, totalServerNumber, allStubs, stubIdx),
// 					0, 0, allStubs, stubIdx);
// 			if (stubIdx == totalServerNumber - 1) {
// 				stubIdx = 0;
// 			} else {
// 				stubIdx = stubIdx + 1;
// 			}

// 			// C12
// 			matrixAddSummation(rsltMatrix,
// 					addMatrix(firstMatrix, secondMatrix, rowfst, colfst, rowscd, colscd + newmtxSize, newmtxSize,
// 							totalServerNumber, allStubs, stubIdx),
// 					addMatrix(firstMatrix, secondMatrix, rowfst, colfst + newmtxSize, rowscd + newmtxSize,
// 							colscd + newmtxSize, newmtxSize, totalServerNumber, allStubs, stubIdx),
// 					0, newmtxSize, allStubs, stubIdx);
// 			if (stubIdx == totalServerNumber - 1) {
// 				stubIdx = 0;
// 			} else {
// 				stubIdx = stubIdx + 1;
// 			}

// 			// C21
// 			matrixAddSummation(rsltMatrix,
// 					addMatrix(firstMatrix, secondMatrix, rowfst + newmtxSize, colfst, rowscd, colscd, newmtxSize,
// 							totalServerNumber, allStubs, stubIdx),
// 					addMatrix(firstMatrix, secondMatrix, rowfst + newmtxSize, colfst + newmtxSize, rowscd + newmtxSize,
// 							colscd, newmtxSize, totalServerNumber, allStubs, stubIdx),
// 					newmtxSize, 0, allStubs, stubIdx);
// 			if (stubIdx == totalServerNumber - 1) {
// 				stubIdx = 0;
// 			} else {
// 				stubIdx = stubIdx + 1;
// 			}

// 			// C22
// 			matrixAddSummation(rsltMatrix,
// 					addMatrix(firstMatrix, secondMatrix, rowfst + newmtxSize, colfst, rowscd, colscd + newmtxSize,
// 							newmtxSize, totalServerNumber, allStubs, stubIdx),
// 					addMatrix(firstMatrix, secondMatrix, rowfst + newmtxSize, colfst + newmtxSize, rowscd + newmtxSize,
// 							colscd + newmtxSize, newmtxSize, totalServerNumber, allStubs, stubIdx),
// 					newmtxSize, newmtxSize, allStubs, stubIdx);
// 			if (stubIdx == totalServerNumber - 1) {
// 				stubIdx = 0;
// 			} else {
// 				stubIdx = stubIdx + 1;
// 			}
// 		}
// 		return rsltMatrix;
// 	}

// 	private static void matrixAddSummation(int[][] rsltMatrix, int[][] firstMatrix, int[][] secondMatrix, int rowRslt,
// 			int colRslt, List<MatrixServiceGrpc.MatrixServiceFutureStub> allStubs, int stubIdx) {
// 		int mtxSizeIter = firstMatrix.length;
// 		int whileIterRow = 0;
// 		int whileIterCol = 0;
// 		while (whileIterRow < mtxSizeIter) {
// 			while (whileIterCol < mtxSizeIter) {
// 				MatrixServiceGrpc.MatrixServiceFutureStub currentStub = allStubs.get(stubIdx);
// 				MatrixRequest mtxRequest = MatrixRequest.newBuilder()
// 						.setA00(firstMatrix[whileIterRow][whileIterCol])
// 						.setB00(secondMatrix[whileIterRow][whileIterCol])
// 						.build();

// 				ListenableFuture<MatrixReply> lstnblFuture = currentStub.addBlock(mtxRequest);
// 				MatrixReply mtxReply = null;

// 				try {
// 					mtxReply = lstnblFuture.get();
// 				} catch (InterruptedException e) {
// 					e.printStackTrace();
// 				} catch (ExecutionException e) {
// 					e.printStackTrace();
// 				}

// 				rsltMatrix[whileIterRow + rowRslt][whileIterCol + colRslt] = mtxReply.getC00();
// 				whileIterCol = whileIterCol + 1;
// 			}
// 			whileIterRow = whileIterRow + 1;
// 		}
// 	}
}
