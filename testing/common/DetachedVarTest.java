/**
 * The point of this test:
 * This test makes sure that array return types result in multiple output return variables
 * Also, the dt.test and d2.test2 calls test instances of desugared ("DetachedVarSymbol")
 * array instances.
 */

class DetachedVarTest {
	public DetachedVarTest [] test() { return null; }
} 


class DetachedVarAccess {
	
	private void method(DetachedVarTest dt) {
		for(final DetachedVarTest dvt : dt.test()) {
			System.out.println(dvt);
		}
	}
	
}