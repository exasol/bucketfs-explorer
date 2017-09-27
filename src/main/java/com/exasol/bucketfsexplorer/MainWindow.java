package com.exasol.bucketfsexplorer;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.apache.xmlrpc.XmlRpcException;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.TextFieldTreeCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Callback;

public class MainWindow extends Application {

	public static final ObservableList<String> data = FXCollections.observableArrayList();

	private final Image bucketFSIcon = new Image(getClass().getResourceAsStream("/ic_cloud_black_18dp.png"));

	private final Image bucketIcon = new Image(getClass().getResourceAsStream("/ic_folder_black_18dp.png"));

	private final Image exasol = new Image(this.getClass().getResourceAsStream("/exasol.png"));

	private ArrayList<BucketFS> bucketFSList = new ArrayList<BucketFS>();

	private TreeItem<BucketObject> rootNode = new TreeItem<BucketObject>();

	private Configuration config = null;

	private XmlRPCAccessLayer xmlRPC = null;

	private GridPane objectInfo = null;

	private boolean xmlRPCConnectionWorks = false;

	private TreeView<BucketObject> treeView = null;

	private Stage stage;

	public static void main(String[] args) {
		Application.launch(args);
	}

	@Override
	public void start(Stage stage) {

		this.stage = stage;

		stage.setTitle("BucketFS Explorer");

		stage.getIcons().add(exasol);

		// First establish xmlRPC Connection
		if (!establishXMLRPCConnection())
			return;

		BorderPane border = new BorderPane();

		final Scene scene = new Scene(border, 600, 400);

		scene.setFill(Color.LIGHTGRAY);

		treeView = new TreeView<BucketObject>(rootNode);

		reloadTree();

		treeView.setPrefWidth(200);

		treeView.setShowRoot(false);

		treeView.setEditable(false);

		// defines a custom tree cell factory for the tree view
		treeView.setCellFactory(new Callback<TreeView<BucketObject>, TreeCell<BucketObject>>() {

			@Override
			public TreeCell<BucketObject> call(TreeView<BucketObject> arg0) {
				// custom tree cell that defines a context menu for the root
				// tree item
				return new MyBucketTreeCell(stage);
			}
		});

		treeView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {

			if (newValue != null) {

				reloadFilesOfBucket(newValue.getValue());
				reloadObjectInfo(newValue.getValue());

			}

		});

		border.setLeft(treeView);

		final ListView<String> listView = new ListView<String>(data);
		listView.setPrefSize(200, 250);
		listView.setEditable(false);

		listView.setItems(data);

		listView.setOnDragOver(new EventHandler<DragEvent>() {

            @Override
            public void handle(DragEvent event) {
                if (event.getGestureSource() != listView
                        && event.getDragboard().hasFiles()) {
                    /* allow for both copying and moving, whatever user chooses */
                    event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                }
                event.consume();
            }
        });

		listView.setOnDragDropped(new EventHandler<DragEvent>() {

            @Override
            public void handle(DragEvent event) {
                Dragboard db = event.getDragboard();
                boolean success = false;
                if (db.hasFiles()) {
                	
    				TreeItem<BucketObject> bObj = treeView.getSelectionModel().getSelectedItem();

    				if (bObj.getValue() instanceof Bucket) {
    					uploadFiles(db.getFiles(), (Bucket)bObj.getValue());
                    	success = true;
    				}
                	
                }
                
                event.setDropCompleted(success);

                event.consume();
            }
        });

		listView.setCellFactory(lv -> {

			ListCell<String> cell = new ListCell<>();

			ContextMenu contextMenu = new ContextMenu();

			MenuItem deleteItem = new MenuItem();
			deleteItem.textProperty().bind(Bindings.format("Delete \"%s\"", cell.itemProperty()));
			deleteItem.setOnAction(event -> {

				TreeItem<BucketObject> bObj = treeView.getSelectionModel().getSelectedItem();

				if (bObj.getValue() instanceof Bucket) {

					Alert alert = new Alert(AlertType.CONFIRMATION);
					alert.setTitle("Confirm delete.");
					alert.setHeaderText("Delete file " + cell.getItem() + "?");
					alert.setContentText("Do you really want to delete this file?");

					Optional<ButtonType> result = alert.showAndWait();

					if (result.get() != ButtonType.OK)
						return;

					Bucket b = (Bucket) bObj.getValue();

					if (b.getWritePassword() == null)
						b.setWritePassword(showPasswordDialog("Write password for " + b.getName(), ""));

					Platform.runLater(new Runnable() {

						@Override
						public void run() {

							try {

								b.deleteFile(cell.getItem());
							} catch (IOException | URISyntaxException | XmlRpcException | KeyManagementException | NoSuchAlgorithmException | KeyStoreException e) {
								Alert alert = new Alert(AlertType.ERROR);
								alert.setTitle("Error during delete");
								alert.setHeaderText(
										"Can't delete file " + cell.getItem() + " in bucket " + b.getName() + ".");
								alert.setContentText(e.getMessage());
								alert.showAndWait();
							}

							listView.getItems().remove(cell.getItem());

							Platform.runLater(new Runnable() {

								@Override
								public void run() {

									try {
										b.reloadMetadata();
									} catch (MalformedURLException | XmlRpcException e) {
										
										Alert alert = new Alert(AlertType.ERROR);
										alert.setTitle("Error during reload");
										alert.setContentText(e.getMessage());
										alert.showAndWait();
										
									}

									reloadObjectInfo(b);

								}
							});

						}
					});

				}

			});

			contextMenu.getItems().addAll(deleteItem);

			
			MenuItem downloadItem = new MenuItem();
			downloadItem.textProperty().bind(Bindings.format("Download \"%s\"", cell.itemProperty()));
			downloadItem.setOnAction(event -> {

				TreeItem<BucketObject> bObj = treeView.getSelectionModel().getSelectedItem();

				if (bObj.getValue() instanceof Bucket) {

					Bucket b = (Bucket) bObj.getValue();

					if (!b.isPublic() && b.getReadPassword() == null)
						b.setReadPassword(showPasswordDialog("Read password for " + b.getName(), ""));

					DirectoryChooser directoryChooser = new DirectoryChooser();
					directoryChooser.setTitle("Select target directory");
					
					File targetDirectory = directoryChooser.showDialog(stage);
					
					File targetFile = new File(targetDirectory, cell.getItem().replaceAll("/", "%2"));
					
					
					if(targetFile.exists()) {
						
						Alert alert = new Alert(AlertType.CONFIRMATION);
						alert.setTitle("Confirm download");
						alert.setHeaderText("File " + targetFile.getAbsolutePath() + "already exists?");
						alert.setContentText("Do you want to overwrite this file?");

						Optional<ButtonType> result = alert.showAndWait();

						if (result.get() != ButtonType.OK)
							return;
					}
					
					Platform.runLater(new Runnable() {

						@Override
						public void run() {

							try {
								b.downloadFile(targetDirectory,cell.getItem());
								
								Alert alert = new Alert(AlertType.INFORMATION);
								alert.setTitle("Download finished");
								alert.setHeaderText("Download finished");
								alert.setContentText("File " + cell.getItem() + " in bucket " + b.getName() + " to " + targetFile.getAbsolutePath() + ".");
								alert.showAndWait();
								
							} catch (IOException | URISyntaxException | KeyManagementException | NoSuchAlgorithmException | KeyStoreException e) {
								Alert alert = new Alert(AlertType.ERROR);
								alert.setTitle("Error during download");
								alert.setHeaderText(
										"Can't download file " + cell.getItem() + " in bucket " + b.getName() + ".");
								alert.setContentText(e.getMessage());
								alert.showAndWait();
							}

						}
					});

				}

			});

			contextMenu.getItems().addAll(downloadItem);
			
			cell.textProperty().bind(cell.itemProperty());

			
			cell.emptyProperty().addListener((obs, wasEmpty, isNowEmpty) -> {
                if (isNowEmpty) {
                    cell.setContextMenu(null);
                } else {
                    cell.setContextMenu(contextMenu);
                }
            });
			
			//cell.setContextMenu(contextMenu);

			return cell;
		});

		border.setCenter(listView);

		objectInfo = new GridPane();

		objectInfo.setHgap(5);
		objectInfo.setVgap(5);

		objectInfo.setPadding(new Insets(5));

		border.setBottom(objectInfo);

		stage.setScene(scene);
		stage.show();
	}

	private boolean establishXMLRPCConnection() {

		
		
		
		while (!xmlRPCConnectionWorks) {

			Optional<Configuration> opt = showLoginDialog(config);

			if (opt.isPresent())
				config = opt.get();
			else
				return false;

			// Check if xmlRPC works
			try {
				xmlRPC = new XmlRPCAccessLayer(config);

				xmlRPC.listObjects();

				xmlRPCConnectionWorks = true;

			} catch (MalformedURLException | XmlRpcException e) {

				Alert alert = new Alert(AlertType.ERROR);
				alert.setTitle("Error during connect");
				alert.setHeaderText(
						"Can't connect to " + config.getUrl() + ", please try again (maybe wrong credentials?)");
				alert.setContentText(e.getMessage());
				alert.showAndWait();

			}

		}

		return true;

	}

	private void reloadTree() {

		treeView.getRoot().getChildren().clear();

		try {
			initBucketFSStructure();
		} catch (Exception e) {
			
			Alert alert = new Alert(AlertType.ERROR);
			alert.setTitle("Error during initBucketFSStructure");
			alert.setContentText(e.getStackTrace().toString());
			alert.showAndWait();
			
		}

		for (Iterator<BucketFS> iterator = bucketFSList.iterator(); iterator.hasNext();) {
			BucketFS bucketFS = (BucketFS) iterator.next();

			TreeItem<BucketObject> tBFs = new TreeItem<BucketObject>(bucketFS, new ImageView(bucketFSIcon));

			ArrayList<Bucket> buckets = bucketFS.getBuckets();

			for (Iterator<Bucket> iterator2 = buckets.iterator(); iterator2.hasNext();) {
				Bucket bucket = (Bucket) iterator2.next();

				tBFs.getChildren().add(new TreeItem<BucketObject>(bucket, new ImageView(bucketIcon)));

			}

			rootNode.getChildren().add(tBFs);
			rootNode.setExpanded(true);
		}

	}

	private void reloadObjectInfo(BucketObject obj) {

		objectInfo.getChildren().clear();

		if (obj instanceof Bucket) {

			Bucket b = (Bucket) obj;

			objectInfo.add(new Label("Name"), 0, 0);

			TextField bucketName = new TextField(b.getName());

			// bucketName.setPrefWidth(80);

			bucketName.setEditable(false);
			
			bucketName.prefColumnCountProperty().bind(bucketName.textProperty().length());

			objectInfo.add(bucketName, 1, 0);

			objectInfo.add(new Label("Description"), 2, 0);

			TextField bucketDesc = new TextField(b.getDescription());

			bucketDesc.prefColumnCountProperty().bind(bucketDesc.textProperty().length());

			bucketDesc.setMaxWidth(200);

			objectInfo.add(bucketDesc, 3, 0);

			objectInfo.add(new Label("Public"), 4, 0);

			CheckBox isPub = new CheckBox();

			isPub.setSelected(b.isPublic());

			objectInfo.add(isPub, 5, 0);

			objectInfo.add(new Label("Size"), 0, 1);

			TextField size = new TextField(humanReadableByteCount(b.getSize(), false));

			size.setPrefWidth(80);

			size.setEditable(false);

			objectInfo.add(size, 1, 1);

			objectInfo.add(new Label("Path"), 2, 1);

			TextField path = new TextField(
					"/buckets/" + b.getBucketFS().getId().toLowerCase() + "/" + b.getName().toLowerCase());

			path.setPrefWidth(200);

			path.setEditable(false);

			objectInfo.add(path, 3, 1);

			Button saveButton = new Button("Save");

			saveButton.setDisable(true);
			
			objectInfo.add(saveButton, 5, 1);
			

			ChangeListener<Object> textChanged = new ChangeListener<Object>() {

				@Override
				public void changed(ObservableValue<? extends Object> observable, Object oldValue, Object newValue) {
					
					saveButton.setDisable(false);

				}
			};

			bucketDesc.textProperty().addListener(textChanged);
		
			isPub.selectedProperty().addListener(textChanged);
			
			saveButton.setOnAction(new EventHandler<ActionEvent>() {
	            @Override 
	            public void handle(ActionEvent e) {
	            	
	            	try {
						
	            		b.editBucket(bucketDesc.getText(),isPub.isSelected());
	            		
						saveButton.setDisable(true);
					} catch (XmlRpcException | MalformedURLException e1) {
						Alert alert = new Alert(AlertType.ERROR);
						alert.setTitle("Error during editing Bucket.");
						alert.setHeaderText("Can't edit " + b.getId() + ".");
						alert.setContentText(e1.getMessage());
						alert.showAndWait();
					}
						
	            }
	        });			
			
			
		} else if (obj instanceof BucketFS) {
			BucketFS b = (BucketFS) obj;

			objectInfo.add(new Label("Name"), 0, 0);

			TextField bucketName = new TextField(b.getId());

			bucketName.setEditable(false);
			
			// bucketName.prefColumnCountProperty().bind(bucketName.textProperty().length());

			bucketName.setMinWidth(50);

			objectInfo.add(bucketName, 1, 0);

			objectInfo.add(new Label("Description"), 2, 0);

			TextField bucketDesc = new TextField(b.getDescription());

			// bucketDesc.prefColumnCountProperty().bind(bucketDesc.textProperty().length());

			bucketDesc.setMaxWidth(200);

			objectInfo.add(bucketDesc, 3, 0);

			objectInfo.add(new Label("Disk"), 4, 0);

			TextField disk = new TextField(b.getDisk());

			disk.setEditable(false);
			
			disk.setMaxWidth(70);

			objectInfo.add(disk, 5, 0);

			objectInfo.add(new Label("HTTP Port"), 0, 1);

			TextField httpPort = new TextField(b.getHttpPort() == null ? "Not set" : b.getHttpPort().toString());

			httpPort.setMinWidth(50);

			objectInfo.add(httpPort, 1, 1);

			objectInfo.add(new Label("HTTPS Port"), 2, 1);

			TextField httpsPort = new TextField((b.getHttpsPort() == null ? "Not set" : b.getHttpsPort().toString()));

			httpsPort.setMinWidth(50);

			objectInfo.add(httpsPort, 3, 1);

			objectInfo.add(new Label("Size"), 4, 1);

			TextField size = new TextField(humanReadableByteCount(b.getSize(), false));
					
			size.setMaxWidth(70);

			size.setEditable(false);

			objectInfo.add(size, 5, 1);
			
			Button saveButton = new Button("Save");

			saveButton.setDisable(true);
			
			objectInfo.add(saveButton, 6, 1);
			
			ChangeListener<String> textChanged = new ChangeListener<String>() {

				@Override
				public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {


					if (httpPort.getText().matches("[0-9]*") || httpPort.getText().equals("Not set"))
						httpPort.setStyle("");
					else
						httpPort.setStyle("-fx-control-inner-background: red");
					
					if (httpsPort.getText().matches("[0-9]*") || httpsPort.getText().equals("Not set"))
						httpsPort.setStyle("");
					else
						httpsPort.setStyle("-fx-control-inner-background: red");
					
					
					if ( ( httpPort.getText().equals("Not set")|| httpPort.getText().matches("[0-9]*" ) ) && 
						( httpsPort.getText().equals("Not set")|| httpsPort.getText().matches("[0-9]*" ) ) ) {

						saveButton.setDisable(false);

					} else
						saveButton.setDisable(true);

				}
			};

			bucketDesc.textProperty().addListener(textChanged);
		
			httpPort.textProperty().addListener(textChanged);

			httpsPort.textProperty().addListener(textChanged);
			
			saveButton.setOnAction(new EventHandler<ActionEvent>() {
	            @Override 
	            public void handle(ActionEvent e) {
	            	
	            	try {
						b.editBucketFs(bucketDesc.getText(),httpPort.getText(),httpsPort.getText());
						
						saveButton.setDisable(true);
					} catch (XmlRpcException e1) {
						Alert alert = new Alert(AlertType.ERROR);
						alert.setTitle("Error during editing BucketFS.");
						alert.setHeaderText("Can't edit " + b.getId() + ".");
						alert.setContentText(e1.getMessage());
						alert.showAndWait();
					} finally {
						
						//if something went wrong the old state will be displayed
						//reloadObjectInfo(b);
					}
	            }
	        });			
		}
	}

	private static String humanReadableByteCount(long bytes, boolean si) {
		int unit = si ? 1000 : 1024;
		if (bytes < unit)
			return bytes + " B";
		int exp = (int) (Math.log(bytes) / Math.log(unit));
		String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
		return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
	}

	private void reloadFilesOfBucket(BucketObject obj) {

		
		data.clear();
		
		if (obj instanceof Bucket) {
		
			Bucket bucket = (Bucket) obj;

			// Check if bucket has http/https port set
			if (bucket.getBucketFS().isNoPortSet()) {

				Alert alert = new Alert(AlertType.WARNING);
				alert.setTitle("BucketFS port(s) not set.");
				alert.setHeaderText("Can't load files for this bucket.");
				alert.setContentText(
						"Can't load files for this bucket because no http or https port is set for BucketFS "
								+ bucket.getBucketFS().getId() + ".");
				alert.showAndWait();
				return;
			}

			if (!bucket.isPublic() && bucket.getReadPassword() == null)
				bucket.setReadPassword(showPasswordDialog("Read password for " + bucket.getName(), ""));

			Platform.runLater(new Runnable() {
				public void run() {
					try {
						List<String> files = bucket.getFiles();

						data.addAll(files);

						data.sort(Comparator.naturalOrder());

					} catch (Exception ex) {

						Alert alert = new Alert(AlertType.ERROR);
						alert.setTitle("Error during file listing");
						alert.setHeaderText("Can't load files for this bucket.");
						alert.setContentText(ex.getMessage());
						alert.showAndWait();

					}

				}
			});
		}

	}

	private void initBucketFSStructure() throws XmlRpcException, MalformedURLException {

		bucketFSList = new ArrayList<BucketFS>();

		Object[] bucketFSs = xmlRPC.listBucketFSs();

		for (int i = 0; i < bucketFSs.length; i++) {
			HashMap<String, Object> props = xmlRPC.getPropertiesBucketFS((String) bucketFSs[i]);

			String desc = (String) props.get("description");
			Integer httpPort = (Integer) props.get("http_port");
			Integer httpsPort = (Integer) props.get("https_port");
			String disk = (String) props.get("disk");

			BucketFS bFS = new BucketFS((String) bucketFSs[i], desc, httpPort, httpsPort, disk, config, xmlRPC);

			Integer size = xmlRPC.getSizeOfBucketFS((String) bucketFSs[i]);

			bFS.setSize(size);

			// add buckets
			Object[] buckets = xmlRPC.listObjects((String) bucketFSs[i]);

			for (int j = 0; j < buckets.length; j++) {

				HashMap<String, Object> bucketProps = xmlRPC.getPropertiesBucket((String) bucketFSs[i],
						(String) buckets[j]);

				Integer bucketSize = xmlRPC.getSizeOfBucket((String) bucketFSs[i], (String) buckets[j]);

				Bucket b = new Bucket((String) buckets[j], bFS);

				b.setName((String) bucketProps.get("bucket_name"));

				b.setPublic((Boolean) bucketProps.get("public_bucket"));

				b.setDescription((String) bucketProps.get("description"));

				b.setSize(bucketSize);

				bFS.addBucket(b);
			}

			bucketFSList.add(bFS);
		}

	}

	private String showPasswordDialog(String title, String headerText) {

		// Create the custom dialog.
		Dialog<String> dialog = new Dialog<>();

		dialog.initOwner(stage);

		dialog.setTitle(title);
		dialog.setHeaderText(headerText);

		// Set the button types.
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(10);
		// grid.setPadding(new Insets(20, 150, 10, 10));

		PasswordField passwordField = new PasswordField();
		passwordField.setPromptText("Password");

		grid.add(new Label("Password:"), 0, 0);
		grid.add(passwordField, 1, 0);

		dialog.getDialogPane().setContent(grid);

		dialog.showAndWait();

		Platform.runLater(() -> passwordField.requestFocus());

		String password = "";

		if (passwordField.getText() != null) {
			password = passwordField.getText();
		}

		return password;
	}

	private Optional<Configuration> showLoginDialog(Configuration config2) {

		// Create the custom dialog.
		Dialog<Configuration> dialog = new Dialog<>();

		dialog.setTitle("EXAoperation login");
		dialog.setHeaderText("EXAoperation login");

		dialog.initStyle(StageStyle.UTILITY);

		// Set the icon (must be included in the project).
		// dialog.setGraphic(new
		// ImageView(this.getClass().getResource("login.png").toString()));

		// Set the button types.
		ButtonType loginButtonType = new ButtonType("Login", ButtonData.OK_DONE);
		dialog.getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

		// Create the username and password labels and fields.
		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(10);
		grid.setPadding(new Insets(20, 150, 10, 10));

		TextField exaoperationURL = new TextField();
		
		if(config2 != null)
			exaoperationURL.setText(config2.getUrl());
		else
			exaoperationURL.setText("https://");

		
		exaoperationURL.setPromptText("https://license_server");

		TextField username = new TextField();
		
		if(config2 != null)
			username.setText(config2.getUsername());
		else
			username.setText("admin");
		
		username.setPromptText("Username");

		PasswordField password = new PasswordField();
		
		if(config2 != null)
			password.setText(config2.getPassword());
		
		password.setPromptText("Password");

		grid.add(new Label("EXAoperation URL"), 0, 0);
		grid.add(exaoperationURL, 1, 0);
		grid.add(new Label("Username"), 0, 1);
		grid.add(username, 1, 1);
		grid.add(new Label("Password"), 0, 2);
		grid.add(password, 1, 2);

		// Enable/Disable login button depending on whether a username was
		// entered.
		Node loginButton = dialog.getDialogPane().lookupButton(loginButtonType);
		loginButton.setDisable(true);

		// Do some validation (using the Java 8 lambda syntax).
		username.textProperty().addListener((observable, oldValue, newValue) -> {
			loginButton.setDisable((!MainWindow.validateHTTP_URI(exaoperationURL.getText()))
					| username.getText().trim().isEmpty() | password.getText().trim().isEmpty());
		});

		exaoperationURL.textProperty().addListener((observable, oldValue, newValue) -> {

			if (MainWindow.validateHTTP_URI(newValue))
				exaoperationURL.setStyle("");
			else
				exaoperationURL.setStyle("-fx-control-inner-background: red");

			loginButton.setDisable((!MainWindow.validateHTTP_URI(exaoperationURL.getText()))
					| username.getText().trim().isEmpty() | password.getText().trim().isEmpty());
		});

		password.textProperty().addListener((observable, oldValue, newValue) -> {
			loginButton.setDisable((!MainWindow.validateHTTP_URI(exaoperationURL.getText()))
					| username.getText().trim().isEmpty() | password.getText().trim().isEmpty());
		});

		dialog.getDialogPane().setContent(grid);

		// Request focus on the username field by default.
		Platform.runLater(() -> exaoperationURL.requestFocus());

		// Convert the result to a username-password-pair when the login button
		// is clicked.
		dialog.setResultConverter(dialogButton -> {
			if (dialogButton == loginButtonType) {

				return new Configuration(exaoperationURL.getText(), username.getText(), password.getText());
			}
			return null;
		});

		Optional<Configuration> result = dialog.showAndWait();

		return result;
	}

	private static boolean validateHTTP_URI(String uri) {
		final URL url;
		try {
			url = new URL(uri);
		} catch (Exception e1) {
			return false;
		}
		return "http".equals(url.getProtocol()) || "https".equals(url.getProtocol());
	}

	
	private void uploadFiles(List<File> files, Bucket bucket) {
		
		if (files != null) {

			if (bucket.getWritePassword() == null)
				bucket.setWritePassword(showPasswordDialog("Write password for " + bucket.getName(), ""));

			
			Service<Void> service = new Service<Void>() {
			    @Override
			    protected Task<Void> createTask() {
			        return new Task<Void>() {
			            @Override
			            protected Void call()
			                    throws InterruptedException {
			                updateMessage("Uploading files. . .");
			                
			                
			                int i =0;
			                updateProgress(i, files.size());
			                
			                try {

								for (File file : files) {
									bucket.uploadFile(file);
					                updateProgress(++i, files.size());
				                    updateMessage("Uploaded "+file.getName());
									
								}

							} catch (IOException | URISyntaxException | XmlRpcException | KeyManagementException | NoSuchAlgorithmException | KeyStoreException e) {

								Alert alert = new Alert(AlertType.ERROR);
								alert.setTitle("Error during upload");
								alert.setHeaderText("Can't upload file to " + bucket.getName());
								alert.setContentText(e.getMessage());
								alert.showAndWait();
							}

			                updateMessage("Upload finished.");
			               
			                
							Platform.runLater(new Runnable() {

								@Override
								public void run() {

									try {
										
										reloadFilesOfBucket(bucket);
										
										bucket.reloadMetadata();
									} catch (MalformedURLException | XmlRpcException e) {
										Alert alert = new Alert(AlertType.ERROR);
										alert.setTitle("Error during upload");
										alert.setHeaderText("Can't upload file to " + bucket.getName());
										alert.setContentText(e.getMessage());
										alert.showAndWait();
									}

									reloadObjectInfo(bucket);
								}
							});
			               						                
			                return null;
			            }
			        };
			    }
			};

			Dialog<Boolean> progressDialog = new Dialog<Boolean>();

			progressDialog.setTitle("Upload progress");
			progressDialog.initOwner(stage);

			// Create the username and password labels and fields.
			GridPane grid = new GridPane();
			grid.setHgap(10);
			grid.setVgap(10);
			
			//grid.setPadding(new Insets(20, 150, 10, 10));

			Label statusLabel = new Label("Starting upload");
			
			grid.add(statusLabel, 0, 0);

			ProgressBar progbar = new ProgressBar();
			
			progbar.setMinWidth(200);
			
			grid.add(progbar, 0, 1);

			progressDialog.getDialogPane().setContent(grid);
			

			progbar.progressProperty().bind(service.progressProperty());
			
            statusLabel.textProperty().bind(service.messageProperty());
			
			progressDialog.show();
			
			service.setOnSucceeded(value -> {
				progressDialog.setResult(Boolean.TRUE);
		        progressDialog.close();					    
		     }
			);
			
			service.setOnFailed(value -> {
				progressDialog.setResult(Boolean.FALSE);
		        progressDialog.close();					    
		     }
			);
			
			service.start();
		}

		
		
	}
	
	class MyBucketTreeCell extends TextFieldTreeCell<BucketObject> {

		Stage stage;

		public MyBucketTreeCell(Stage stage) {
			super();
			this.stage = stage;
		}

		@Override
		public void updateItem(BucketObject item, boolean empty) {
			super.updateItem(item, empty);

			ContextMenu cm = createContextMenu(item);
			setContextMenu(cm);
		}


		private ContextMenu createContextMenu(BucketObject item) {
			ContextMenu cm = new ContextMenu();

			if (item instanceof Bucket) {

				MenuItem openItem = new MenuItem("Upload file(s)");
				openItem.setOnAction(event -> {
					Bucket bucket = (Bucket) item;

					FileChooser fileChooser = new FileChooser();
					fileChooser.setTitle("Choose file(s) to upload to " + bucket.getName());
					List<File> files = fileChooser.showOpenMultipleDialog(stage);

			
					uploadFiles(files, bucket);
					
					
				});

				cm.getItems().add(openItem);

				MenuItem enterReadPassword = new MenuItem("Enter read password (session)");

				enterReadPassword.setOnAction(event -> {
					Bucket bucket = (Bucket) item;

					bucket.setReadPassword(showPasswordDialog("Read password for " + bucket.getName(),
							"Read password for current session"));

				});

				cm.getItems().add(enterReadPassword);
				
				MenuItem changeReadPassword = new MenuItem("Change read password (server)");

				changeReadPassword.setOnAction(event -> {
					Bucket bucket = (Bucket) item;

					try {
						bucket.changeReadPassword(showPasswordDialog("Change read password for " + bucket.getName(),
								"Change read password permanently."));
					} catch (MalformedURLException | XmlRpcException e) {
						Alert alert = new Alert(AlertType.ERROR);
						alert.setTitle("Error during password change.");
						alert.setContentText(e.getMessage());
						alert.showAndWait();
					}

				});
				
				cm.getItems().add(changeReadPassword);

				MenuItem enterWritePassword = new MenuItem("Enter write password (session)");

				enterWritePassword.setOnAction(event -> {
					Bucket bucket = (Bucket) item;

					bucket.setWritePassword(showPasswordDialog("Write password for " + bucket.getName(),
							"Write password for current session"));

				});

				cm.getItems().add(enterWritePassword);

				
				MenuItem changeWritePassword = new MenuItem("Change write password (server)");

				changeWritePassword.setOnAction(event -> {
					Bucket bucket = (Bucket) item;

					try {
						bucket.changeWritePassword(showPasswordDialog("Change write password for " + bucket.getName(),
								"Change write password permanently."));
					} catch (MalformedURLException | XmlRpcException e) {
						Alert alert = new Alert(AlertType.ERROR);
						alert.setTitle("Error during password change.");
						alert.setContentText(e.getMessage());
						alert.showAndWait();
					}

				});
				
				cm.getItems().add(changeWritePassword);
				
				// other menu items...
				MenuItem refreshBucketItem = new MenuItem("Refresh metadata of bucket");
				refreshBucketItem.setOnAction(event -> {

					Bucket bucket = (Bucket) item;

					try {
						bucket.reloadMetadata();
					} catch (MalformedURLException | XmlRpcException e) {
						Alert alert = new Alert(AlertType.ERROR);
						alert.setTitle("Error during reload.");
						alert.setContentText(e.getMessage());
						alert.showAndWait();
					}

					reloadObjectInfo(bucket);

				});

				cm.getItems().add(refreshBucketItem);
				
				// other menu items...
				MenuItem deleteBucket = new MenuItem("Delete bucket");
				deleteBucket.setOnAction(event -> {

					Alert alert = new Alert(AlertType.CONFIRMATION);
					alert.setTitle("Confirm recursive delete.");
					alert.setHeaderText("Delete bucket " + item+ "?");
					alert.setContentText("Do you really want to delete this bucket and all containing files?");

					Optional<ButtonType> result = alert.showAndWait();

					if (result.get() != ButtonType.OK)
						return;
					
					Bucket bucket = (Bucket) item;
					
					if (!bucket.isPublic() && bucket.getReadPassword() == null)
						bucket.setReadPassword(showPasswordDialog("Read password for " + bucket.getName(), ""));
					
					if (bucket.getWritePassword() == null)
						bucket.setWritePassword(showPasswordDialog("Write password for " + bucket.getId(), ""));
					
					try {
						bucket.getBucketFS().deleteBucket(bucket);
						
						 TreeItem<BucketObject> c = (TreeItem<BucketObject>)treeView.getSelectionModel().getSelectedItem();
				         c.getParent().getChildren().remove(c);
						
					} catch (XmlRpcException | KeyManagementException | NoSuchAlgorithmException | KeyStoreException | IOException | URISyntaxException e) {
						
						Alert alert2 = new Alert(AlertType.ERROR);
						alert2.setTitle("Error during bucket delete");
						alert2.setContentText(e.getMessage());
						alert2.showAndWait();
						
					} 
					
				});

				cm.getItems().add(deleteBucket);

			} else if (item instanceof BucketFS) {

				MenuItem createBucket = new MenuItem("Create bucket");

				createBucket.setOnAction(event -> {
					BucketFS bucketFS = (BucketFS) item;

					Optional<Bucket> opt = openCreateBucketDialog(bucketFS);

					Platform.runLater(new Runnable() {
						
						@Override
						public void run() {
							if (opt.isPresent()) {

								Bucket b = opt.get();

								try {
									bucketFS.createBucket(b);
								} catch (XmlRpcException e) {

									Alert alert = new Alert(AlertType.ERROR);
									alert.setTitle("Error during bucket creation");
									alert.setHeaderText("Can't create " + b.getName() + ".");
									alert.setContentText(e.getMessage());
									alert.showAndWait();
								}

								treeView.getSelectionModel().getSelectedItem().getChildren()
										.add(new TreeItem<BucketObject>(b, new ImageView(bucketIcon)));

							}
							
						}
					});

				});

				cm.getItems().add(createBucket);

				MenuItem deleteBucketFS = new MenuItem("Delete BucketFS");
				deleteBucketFS.setOnAction(event -> {
					
					BucketFS bucketFS = (BucketFS) item;

					if( bucketFS.getBuckets().size() > 0 ) {
						
						Alert alert = new Alert(AlertType.ERROR);
						alert.setTitle("BucketFS not empty");
						alert.setHeaderText("The BucketFS " + item+ " is not empty!");
						alert.setContentText("Can't delete this BucketFS, because it still contains buckets.");
						alert.showAndWait();
						
						return;
					}
					
					Alert alert = new Alert(AlertType.CONFIRMATION);
					alert.setTitle("Confirm delete.");
					alert.setHeaderText("Delete BucketFS " + item+ "?");
					alert.setContentText("Do you really want to delete this BucketFS");
					
					Optional<ButtonType> result = alert.showAndWait();

					if (result.get() != ButtonType.OK)
						return;
					
					Platform.runLater(new Runnable() {
						public void run() {
							// delete bucketFS 
							try {
								bucketFS.delete();
								
								// delete in Tree
								TreeItem<BucketObject> c = (TreeItem<BucketObject>)treeView.getSelectionModel().getSelectedItem();
						        c.getParent().getChildren().remove(c);
								
							} catch (XmlRpcException e) {
								
								Alert alert1 = new Alert(AlertType.ERROR);
								alert1.setTitle("Error during BucketFS deletion");
								alert1.setHeaderText("Can't delete " + bucketFS.getId() + ".");
								alert1.setContentText(e.getMessage());
								alert1.showAndWait();
							}
						}
					});
					
				});

				cm.getItems().add(deleteBucketFS);

			}

			MenuItem createBucket = new MenuItem("Create BucketFS");

			createBucket.setOnAction(event -> {
				
				Optional<BucketFS> opt = openCreateBucketFSDialog(((BucketFS) treeView.getRoot().getChildren().get(0).getValue()).getDisk());

				Platform.runLater(new Runnable() {
					public void run() {
						if (opt.isPresent()) {

							BucketFS bFS = opt.get();

							try {
								bFS.createBucketFS();
							} catch (XmlRpcException e) {

								Alert alert = new Alert(AlertType.ERROR);
								alert.setTitle("Error during BucketFS creation");
								alert.setHeaderText("Can't create " + bFS.getId() + ".");
								alert.setContentText(e.getMessage());
								alert.showAndWait();
							}

							treeView.getRoot().getChildren().add(new TreeItem<BucketObject>(bFS, new ImageView(bucketFSIcon)));

						}
					}
				});
				
			});

			cm.getItems().add(createBucket);
			
			
			// other menu items...

			MenuItem reloadItem = new MenuItem("Reset tree");
			reloadItem.setOnAction(event -> {
				reloadTree();
			});

			cm.getItems().add(reloadItem);

			return cm;
		}

	}

	private TextField bucketName;

	private PasswordField readPasswordField;

	private PasswordField writePasswordField;

	private Node okButton;

	private Optional<Bucket> openCreateBucketDialog(BucketFS bucketFS) {

		ChangeListener<String> textChanged = new ChangeListener<String>() {

			@Override
			public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {

				// check name field
				if (bucketName.getText().matches("[a-z0-9]*"))
					bucketName.setStyle("");
				else
					bucketName.setStyle("-fx-control-inner-background: red");

				if (bucketName.getText().matches("[a-z0-9]*") && readPasswordField.getText().length() > 0
						&& writePasswordField.getText().length() > 0) {

					okButton.setDisable(false);

				}else {
					
					okButton.setDisable(true);

				}
			}
		};

		// Create the custom dialog.
		Dialog<Bucket> dialog = new Dialog<>();

		dialog.setTitle("Create Bucket in " + bucketFS.getId());
		dialog.setHeaderText("Create Bucket in " + bucketFS.getId());

		dialog.initOwner(stage);

		// Set the icon (must be included in the project).
		// dialog.setGraphic(new
		// ImageView(this.getClass().getResource("/exasol.png").toString()));

		// Set the button types.
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		// Create the username and password labels and fields.
		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(10);
		grid.setPadding(new Insets(20, 150, 10, 10));

		grid.add(new Label("Bucket"), 0, 0);

		bucketName = new TextField();

		grid.add(bucketName, 1, 0);

		grid.add(new Label("Description"), 0, 1);

		TextField bucketDesc = new TextField();

		grid.add(bucketDesc, 1, 1);

		grid.add(new Label("Public"), 0, 2);

		CheckBox isPub = new CheckBox();

		grid.add(isPub, 1, 2);

		readPasswordField = new PasswordField();
		readPasswordField.setPromptText("Read Password");

		grid.add(new Label("Read Password"), 0, 3);
		grid.add(readPasswordField, 1, 3);

		writePasswordField = new PasswordField();
		writePasswordField.setPromptText("Write Password");

		grid.add(new Label("Write Password"), 0, 4);
		grid.add(writePasswordField, 1, 4);

		// Enable/Disable OK Button depending on all information is entered and
		// valid
		okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);
		okButton.setDisable(true);

		// Do some validation
		bucketName.textProperty().addListener(textChanged);

		bucketDesc.textProperty().addListener(textChanged);

		readPasswordField.textProperty().addListener(textChanged);

		writePasswordField.textProperty().addListener(textChanged);

		dialog.getDialogPane().setContent(grid);

		// Request focus on the bucketName field by default.
		Platform.runLater(() -> bucketName.requestFocus());

		// Convert the result to a bucket
		dialog.setResultConverter(dialogButton -> {
			if (dialogButton == ButtonType.OK) {

				Bucket b = new Bucket(bucketName.getText(), bucketFS);

				b.setName(bucketName.getText());

				b.setDescription(bucketDesc.getText());

				b.setPublic(isPub.isSelected());

				b.setReadPassword(readPasswordField.getText());

				b.setWritePassword(writePasswordField.getText());

				return b;
			}
			return null;
		});

		Optional<Bucket> result = dialog.showAndWait();

		return result;
	}

	private TextField disk;
	
	private TextField httpPort;
	
	private TextField httpsPort ;
	
	private Node okButton2;
	
	public Optional<BucketFS> openCreateBucketFSDialog(String diskName) {
		ChangeListener<String> textChanged = new ChangeListener<String>() {

			@Override
			public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {


				if (httpPort.getText().matches("[0-9]*"))
					httpPort.setStyle("");
				else
					httpPort.setStyle("-fx-control-inner-background: red");
				
				if (httpsPort.getText().matches("[0-9]*"))
					httpsPort.setStyle("");
				else
					httpsPort.setStyle("-fx-control-inner-background: red");
				
				
				if ( ( httpPort.getText().length() == 0 || httpPort.getText().matches("[0-9]*" ) ) && 
					( httpsPort.getText().length() == 0 || httpsPort.getText().matches("[0-9]*" ) ) && 
						
						disk.getText().length() > 0   ) {

					okButton2.setDisable(false);

				} else
					okButton2.setDisable(true);

			}
		};

		// Create the custom dialog.
		Dialog<BucketFS> dialog = new Dialog<>();

		dialog.setTitle("Create BucketFS");
		dialog.setHeaderText("Create BucketFS");

		dialog.initOwner(stage);

		// Set the icon (must be included in the project).
		// dialog.setGraphic(new
		// ImageView(this.getClass().getResource("/exasol.png").toString()));

		// Set the button types.
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		// Create the username and password labels and fields.
		GridPane grid = new GridPane();
		grid.setHgap(10);
		grid.setVgap(10);
		grid.setPadding(new Insets(20, 150, 10, 10));

		grid.add(new Label("Description"), 0, 1);

		TextField bucketFSDesc = new TextField();

		grid.add(bucketFSDesc, 1, 1);

		grid.add(new Label("Disk"), 0, 2);

		disk = new TextField(diskName);

		grid.add(disk, 1,2);

		grid.add(new Label("HTTP Port"), 0, 3);

		httpPort = new TextField();

		grid.add(httpPort, 1, 3);

		grid.add(new Label("HTTPS Port"), 0, 4);

		httpsPort = new TextField();

		grid.add(httpsPort, 1, 4);
			
		// Enable/Disable OK Button depending on all information is entered and
		// valid
		okButton2 = dialog.getDialogPane().lookupButton(ButtonType.OK);
		okButton2.setDisable(true);

		bucketFSDesc.textProperty().addListener(textChanged);

		disk.textProperty().addListener(textChanged);
		
		httpPort.textProperty().addListener(textChanged);
		
		httpsPort.textProperty().addListener(textChanged);
		
		dialog.getDialogPane().setContent(grid);

		// Request focus on the bucketName field by default.
		Platform.runLater(() -> bucketFSDesc.requestFocus());

		// Convert the result to a bucketFS
		dialog.setResultConverter(dialogButton -> {
			if (dialogButton == ButtonType.OK) {

				return new BucketFS( bucketFSDesc.getText(), httpPort.getText().length()>0 ? Integer.valueOf(httpPort.getText()) : null,  httpsPort.getText().length()>0 ? Integer.valueOf(httpsPort.getText()): null, disk.getText(), config, xmlRPC);

			}
			return null;
		});

		Optional<BucketFS> result = dialog.showAndWait();

		return result;
	}

}