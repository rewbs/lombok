import java.io.*;
class CleanupPlain {
	void test() throws Exception {
		InputStream in = new FileInputStream("in");
		try {
			OutputStream out = new FileOutputStream("out");
			try {
				if (in.markSupported()) {
					out.flush();
				}
			} finally {
				out.close();
			}
		} finally {
			in.close();
		}
	}
}
