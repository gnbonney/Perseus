// Generated from Algol.g4 by ANTLR 4.4
package gnb.jalgol.compiler.antlr;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.*;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast"})
public class AlgolLexer extends Lexer {
	static { RuntimeMetaData.checkVersion("4.4", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__7=1, T__6=2, T__5=3, T__4=4, T__3=5, T__2=6, T__1=7, T__0=8, INT_NUM=9, 
		STRING_LITERAL=10, UNTERMINATED_STRING_LITERAL=11, IDENT=12, BLOCK_COMMENT=13, 
		LINE_COMMENT=14, WS=15, NL=16, ANY=17;
	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	public static final String[] tokenNames = {
		"'\\u0000'", "'\\u0001'", "'\\u0002'", "'\\u0003'", "'\\u0004'", "'\\u0005'", 
		"'\\u0006'", "'\\u0007'", "'\b'", "'\t'", "'\n'", "'\\u000B'", "'\f'", 
		"'\r'", "'\\u000E'", "'\\u000F'", "'\\u0010'", "'\\u0011'"
	};
	public static final String[] ruleNames = {
		"T__7", "T__6", "T__5", "T__4", "T__3", "T__2", "T__1", "T__0", "INT_NUM", 
		"STRING_LITERAL", "UNTERMINATED_STRING_LITERAL", "IDENT", "BLOCK_COMMENT", 
		"LINE_COMMENT", "WS", "NL", "ANY"
	};


	public AlgolLexer(CharStream input) {
		super(input);
		_interp = new LexerATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@Override
	public String getGrammarFileName() { return "Algol.g4"; }

	@Override
	public String[] getTokenNames() { return tokenNames; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public String[] getModeNames() { return modeNames; }

	@Override
	public ATN getATN() { return _ATN; }

	public static final String _serializedATN =
		"\3\u0430\ud6d1\u8206\uad2d\u4417\uaef1\u8d80\uaadd\2\23\u008b\b\1\4\2"+
		"\t\2\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4"+
		"\13\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22"+
		"\t\22\3\2\3\2\3\2\3\2\3\2\3\2\3\3\3\3\3\4\3\4\3\4\3\4\3\5\3\5\3\6\3\6"+
		"\3\7\3\7\3\b\3\b\3\t\3\t\3\n\6\n=\n\n\r\n\16\n>\3\13\3\13\3\13\3\f\3\f"+
		"\3\f\3\f\3\f\5\fI\n\f\7\fK\n\f\f\f\16\fN\13\f\3\r\3\r\7\rR\n\r\f\r\16"+
		"\rU\13\r\3\16\3\16\3\16\3\16\3\16\3\16\3\16\3\16\5\16_\n\16\3\16\7\16"+
		"b\n\16\f\16\16\16e\13\16\3\16\3\16\3\16\3\16\3\17\3\17\7\17m\n\17\f\17"+
		"\16\17p\13\17\3\17\5\17s\n\17\3\17\3\17\3\17\3\17\3\20\6\20z\n\20\r\20"+
		"\16\20{\3\20\3\20\3\21\5\21\u0081\n\21\3\21\3\21\5\21\u0085\n\21\3\22"+
		"\6\22\u0088\n\22\r\22\16\22\u0089\3\u0089\2\23\3\3\5\4\7\5\t\6\13\7\r"+
		"\b\17\t\21\n\23\13\25\f\27\r\31\16\33\17\35\20\37\21!\22#\23\3\2\b\6\2"+
		"\f\f\17\17$$^^\4\2C\\c|\6\2\62;C\\aac|\3\2==\4\2\f\f\17\17\5\2\13\f\16"+
		"\17\"\"\u0097\2\3\3\2\2\2\2\5\3\2\2\2\2\7\3\2\2\2\2\t\3\2\2\2\2\13\3\2"+
		"\2\2\2\r\3\2\2\2\2\17\3\2\2\2\2\21\3\2\2\2\2\23\3\2\2\2\2\25\3\2\2\2\2"+
		"\27\3\2\2\2\2\31\3\2\2\2\2\33\3\2\2\2\2\35\3\2\2\2\2\37\3\2\2\2\2!\3\2"+
		"\2\2\2#\3\2\2\2\3%\3\2\2\2\5+\3\2\2\2\7-\3\2\2\2\t\61\3\2\2\2\13\63\3"+
		"\2\2\2\r\65\3\2\2\2\17\67\3\2\2\2\219\3\2\2\2\23<\3\2\2\2\25@\3\2\2\2"+
		"\27C\3\2\2\2\31O\3\2\2\2\33^\3\2\2\2\35j\3\2\2\2\37y\3\2\2\2!\u0084\3"+
		"\2\2\2#\u0087\3\2\2\2%&\7d\2\2&\'\7g\2\2\'(\7i\2\2()\7k\2\2)*\7p\2\2*"+
		"\4\3\2\2\2+,\7*\2\2,\6\3\2\2\2-.\7g\2\2./\7p\2\2/\60\7f\2\2\60\b\3\2\2"+
		"\2\61\62\7+\2\2\62\n\3\2\2\2\63\64\7=\2\2\64\f\3\2\2\2\65\66\7}\2\2\66"+
		"\16\3\2\2\2\678\7.\2\28\20\3\2\2\29:\7\177\2\2:\22\3\2\2\2;=\4\62;\2<"+
		";\3\2\2\2=>\3\2\2\2><\3\2\2\2>?\3\2\2\2?\24\3\2\2\2@A\5\27\f\2AB\7$\2"+
		"\2B\26\3\2\2\2CL\7$\2\2DK\n\2\2\2EH\7^\2\2FI\13\2\2\2GI\7\2\2\3HF\3\2"+
		"\2\2HG\3\2\2\2IK\3\2\2\2JD\3\2\2\2JE\3\2\2\2KN\3\2\2\2LJ\3\2\2\2LM\3\2"+
		"\2\2M\30\3\2\2\2NL\3\2\2\2OS\t\3\2\2PR\t\4\2\2QP\3\2\2\2RU\3\2\2\2SQ\3"+
		"\2\2\2ST\3\2\2\2T\32\3\2\2\2US\3\2\2\2VW\7e\2\2WX\7q\2\2XY\7o\2\2YZ\7"+
		"o\2\2Z[\7g\2\2[\\\7p\2\2\\_\7v\2\2]_\7#\2\2^V\3\2\2\2^]\3\2\2\2_c\3\2"+
		"\2\2`b\n\5\2\2a`\3\2\2\2be\3\2\2\2ca\3\2\2\2cd\3\2\2\2df\3\2\2\2ec\3\2"+
		"\2\2fg\7=\2\2gh\3\2\2\2hi\b\16\2\2i\34\3\2\2\2jn\7\'\2\2km\n\6\2\2lk\3"+
		"\2\2\2mp\3\2\2\2nl\3\2\2\2no\3\2\2\2or\3\2\2\2pn\3\2\2\2qs\7\17\2\2rq"+
		"\3\2\2\2rs\3\2\2\2st\3\2\2\2tu\7\f\2\2uv\3\2\2\2vw\b\17\3\2w\36\3\2\2"+
		"\2xz\t\7\2\2yx\3\2\2\2z{\3\2\2\2{y\3\2\2\2{|\3\2\2\2|}\3\2\2\2}~\b\20"+
		"\3\2~ \3\2\2\2\177\u0081\7\17\2\2\u0080\177\3\2\2\2\u0080\u0081\3\2\2"+
		"\2\u0081\u0082\3\2\2\2\u0082\u0085\7\f\2\2\u0083\u0085\7\17\2\2\u0084"+
		"\u0080\3\2\2\2\u0084\u0083\3\2\2\2\u0085\"\3\2\2\2\u0086\u0088\13\2\2"+
		"\2\u0087\u0086\3\2\2\2\u0088\u0089\3\2\2\2\u0089\u008a\3\2\2\2\u0089\u0087"+
		"\3\2\2\2\u008a$\3\2\2\2\20\2>HJLS^cnr{\u0080\u0084\u0089\4\2\3\2\b\2\2";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}