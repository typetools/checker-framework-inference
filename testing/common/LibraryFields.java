import java.awt.GridBagConstraints;
import java.awt.Insets;


class LibraryFields {

	public void context() {
		GridBagConstraints constr = new GridBagConstraints();
		constr.anchor = GridBagConstraints.LINE_START;
		int fill = constr.fill;
		
		constr.insets = new Insets( 0, 1, 2, 3 );
		//access
		System.out.println( constr.insets.toString() );
		 
		
	}
}