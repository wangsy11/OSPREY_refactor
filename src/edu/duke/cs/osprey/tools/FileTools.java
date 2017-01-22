package edu.duke.cs.osprey.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Iterator;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

public class FileTools {
	
	public static abstract class PathRoot {
		
		protected String type;
		
		public abstract String resolve(String path);
		public abstract String makeRelative(String path);
		public abstract InputStream open(String path);
		
		public String read(String path) {
			try {
				return readStream(open(path));
			} catch (IOException ex) {
				throw new RuntimeException("can't read " + type + ": " + path);
			}
		}
	}
	
	public static class FilePathRoot extends PathRoot {
		
		private File rootFile;
		
		public FilePathRoot() {
			rootFile = null;
		}
		
		public FilePathRoot(String rootPath) {
			this(new File(rootPath));
		}
		
		public FilePathRoot(File rootFile) {
			this.rootFile = rootFile;
			this.type = "file";
		}
		
		@Override
		public String resolve(String path) {
			
			File file = new File(path);
			
			// absolute paths are already resolved
			if (file.isAbsolute()) {
				return path;
			}
			
			// or use the file root
			if (rootFile != null) {
				return new File(rootFile, path).getAbsolutePath();
			}
			
			// or use the current working directory
			return path;
		}
		
		public File resolve(File file) {
			return new File(resolve(file.getPath()));
		}
		
		@Override
		public String makeRelative(String path) {
			
			if (!path.startsWith(rootFile.getAbsolutePath())) {
				throw new IllegalArgumentException("can't make relative, file: " + path + " doesn't match root: " + rootFile.getAbsolutePath());
			}
			
			return path.substring(rootFile.getAbsolutePath().length() + 1);
		}
		
		public File makeRelative(File file) {
			return new File(makeRelative(file.getAbsolutePath()));
		}
		
		@Override
		public FileInputStream open(String path) {
			try {
				return new FileInputStream(resolve(path));
			} catch (FileNotFoundException ex) {
				throw new RuntimeException(ex);
			}
		}
		
		public FileInputStream open(File file) {
			return open(file.getPath());
		}
		
		public String read(File file) {
			return read(file.getPath());
		}
		
		@Override
		public String toString() {
			return "FileRoot:" + rootFile;
		}
	}
	
	public static class ResourcePathRoot extends PathRoot {
		
		public static ResourcePathRoot parentOf(String path) {
			
			// get the parent path of this path
			String parentPath = FilenameUtils.getPrefix(path) + FilenameUtils.getPath(path);
			
			return new ResourcePathRoot(parentPath);
		}
		
		private String rootPath;
		
		public ResourcePathRoot() {
			this.type = "resource";
			this.rootPath = null;
		}
		
		public ResourcePathRoot(String rootPath) {
			
			if (!rootPath.startsWith("/")) {
				throw new IllegalArgumentException("resource root path is not absolute: " + rootPath);
			}
			
			this.rootPath = rootPath;
		}
		
		public ResourcePathRoot(Class<?> rootClass) {
			this("/" + rootClass.getPackage().getName().replace('.', '/'));
		}
		
		@Override
		public String resolve(String path) {
			
			// absolute paths are already resolved
			if (path.startsWith("/")) {
				return path;
			}
			
			if (rootPath == null) {
				throw new IllegalArgumentException("can't resolve path: " + path + "\npath is not absolute and no relative root was set");
			}
			return rootPath + "/" + path;
		}
		
		@Override
		public String makeRelative(String path) {
			
			if (!path.startsWith(rootPath)) {
				throw new IllegalArgumentException("can't make relative, " + path + " doesn't match root: " + rootPath);
			}
			
			return path.substring(rootPath.length() + 1);
		}
		
		@Override
		public InputStream open(String path) {
			
			path = resolve(path);
			
			// try to find the resource
			URL url = FileTools.class.getResource(path);
			if (url == null) {
				throw new RuntimeException("can't find resource on classpath: " + path);
			}
			
			// open it
			try {
				return url.openStream();
			} catch (IOException ex) {
				throw new RuntimeException("can't open resource: " + path, ex);
			}
		}
		
		public File extractToTempFile(String path)
		throws IOException {
			
			try (InputStream in = open(path)) {
				
				String filename = FilenameUtils.getName(path);
				
				// copy the resource to a temp file
				File file = File.createTempFile(FilenameUtils.getBaseName(filename) + ".", "." + FilenameUtils.getExtension(filename));
				file.deleteOnExit();
				try (OutputStream out = new FileOutputStream(file)) {
					IOUtils.copy(in, out);
				}
				
				return file;
			}
		}
		
		@Override
		public String toString() {
			return "ResourceRoot:" + rootPath;
		}
	}
	
	public static FileInputStream openFile(File file) {
		return new FilePathRoot().open(file.getPath());
	}
	
	public static FileInputStream openFile(String path) {
		return openFile(new File(path));
	}
	
	public static InputStream openResource(String path) {
		return new ResourcePathRoot().open(path);
	}
	
	public static InputStream openResource(String path, Class<?> relativeTo) {
		return new ResourcePathRoot(relativeTo).open(path);
	}
	
	public static String readFile(File file) {
		return new FilePathRoot().read(file);
	}
	
	public static String readFile(String path) {
		return readFile(new File(path));
	}
	
	public static String readResource(String path) {
		return new ResourcePathRoot().read(path);
	}
	
	public static String readResource(String path, Class<?> relativeTo) {
		return new ResourcePathRoot(relativeTo).read(path);
	}
	
	private static String readStream(InputStream in)
	throws IOException {
		return IOUtils.toString(in, (Charset)null);
	}
	
	public static Iterable<String> parseLines(String text) {
		
		BufferedReader reader = new BufferedReader(new StringReader(text));
		
		return new Iterable<String>() {
			@Override
			public Iterator<String> iterator() {
				return new Iterator<String>() {
					
					private String nextLine;
					
					{
						nextLine = readLine();
					}
					
					private String readLine() {
						try {
							return reader.readLine();
						} catch (IOException ex) {
							throw new Error("can't read line", ex);
						}
					}

					@Override
					public boolean hasNext() {
						return nextLine != null;
					}

					@Override
					public String next() {
						String line = nextLine;
						nextLine = readLine();
						return line;
					}
				};
			}
		};
	}
}