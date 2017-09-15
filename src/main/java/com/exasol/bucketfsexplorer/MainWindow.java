package com.exasol.bucketfsexplorer;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
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
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
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
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.TextFieldTreeCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
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
					alert.setContentText("Do your really want to delete this file?");

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
							} catch (IOException | URISyntaxException | XmlRpcException e) {
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
										// TODO Auto-generated catch block
										e.printStackTrace();
									}

									reloadObjectInfo(b);

								}
							});

						}
					});

				}

			});

			contextMenu.getItems().addAll(deleteItem);

			cell.textProperty().bind(cell.itemProperty());

			cell.setContextMenu(contextMenu);

			// cell.emptyProperty().addListener((obs, wasEmpty, isNowEmpty) -> {
			// if (isNowEmpty) {
			// cell.setContextMenu(null);
			// } else {
			//
			// }
			// });
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

			Optional<Configuration> opt = showLoginDialog();

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
			// TODO Auto-generated catch block
			e.printStackTrace();
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

		} else if (obj instanceof BucketFS) {
			BucketFS b = (BucketFS) obj;

			objectInfo.add(new Label("Name"), 0, 0);

			TextField bucketName = new TextField(b.getId());

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

		if (obj instanceof Bucket) {
			data.clear();

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

			int size = (Integer) xmlRPC.getSizeOfBucketFS((String) bucketFSs[i]);

			bFS.setSize(size);

			// add buckets
			Object[] buckets = xmlRPC.listObjects((String) bucketFSs[i]);

			for (int j = 0; j < buckets.length; j++) {

				HashMap<String, Object> bucketProps = xmlRPC.getPropertiesBucket((String) bucketFSs[i],
						(String) buckets[j]);

				int bucketSize = (Integer) xmlRPC.getSizeOfBucket((String) bucketFSs[i], (String) buckets[j]);

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

	private Optional<Configuration> showLoginDialog() {

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
		exaoperationURL.setPromptText("e.g. http://localhost");

		TextField username = new TextField();
		username.setPromptText("Username");

		PasswordField password = new PasswordField();
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

					if (files != null) {

						if (bucket.getWritePassword() == null)
							bucket.setWritePassword(showPasswordDialog("Write password for " + bucket.getName(), ""));

						Platform.runLater(new Runnable() {

							// TODO Show some kind of wait dialog

							@Override
							public void run() {
								try {

									for (File file : files)
										bucket.uploadFile(file);

								} catch (IOException | URISyntaxException | XmlRpcException e) {

									Alert alert = new Alert(AlertType.ERROR);
									alert.setTitle("Error during upload");
									alert.setHeaderText("Can't upload file to " + bucket.getName());
									alert.setContentText(e.getMessage());
									alert.showAndWait();
								}

								reloadFilesOfBucket(bucket);

								Platform.runLater(new Runnable() {

									@Override
									public void run() {

										try {
											bucket.reloadMetadata();
										} catch (MalformedURLException | XmlRpcException e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
										}

										reloadObjectInfo(bucket);
									}
								});

							}
						});

					}

				});

				cm.getItems().add(openItem);

				MenuItem enterReadPassword = new MenuItem("Enter read password (session)");

				enterReadPassword.setOnAction(event -> {
					Bucket bucket = (Bucket) item;

					bucket.setReadPassword(showPasswordDialog("Read password for " + bucket.getName(),
							"Read password for current session"));

				});

				cm.getItems().add(enterReadPassword);

				MenuItem enterWritePassword = new MenuItem("Enter write password (session)");

				enterWritePassword.setOnAction(event -> {
					Bucket bucket = (Bucket) item;

					bucket.setWritePassword(showPasswordDialog("Write password for " + bucket.getName(),
							"Write password for current session"));

				});

				cm.getItems().add(enterWritePassword);

				// other menu items...
				MenuItem refreshBucketItem = new MenuItem("Refresh metadata of bucket");
				refreshBucketItem.setOnAction(event -> {

					Bucket bucket = (Bucket) item;

					try {
						bucket.reloadMetadata();
					} catch (MalformedURLException | XmlRpcException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

					reloadObjectInfo(bucket);

				});

				cm.getItems().add(refreshBucketItem);

			} else if (item instanceof BucketFS) {

				MenuItem createBucket = new MenuItem("Create bucket");

				createBucket.setOnAction(event -> {
					BucketFS bucketFS = (BucketFS) item;

					Optional<Bucket> opt = openCreateBucketDialog(bucketFS);

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

				});

				cm.getItems().add(createBucket);

			}

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

}